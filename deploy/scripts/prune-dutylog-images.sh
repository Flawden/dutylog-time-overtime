#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  cat >&2 <<'USAGE'
Usage:
  prune-dutylog-images.sh \
    --image ghcr.io/owner/repo@sha256:<digest> \
    [--keep-newest 5]

Removes old digest references only for the repository named by --image.

Safety boundaries:
  - the requested image is always retained;
  - every image referenced by any Docker container is retained;
  - the newest N repository images are retained;
  - only matching repository digest references are removed;
  - no force removal, global prune, containerd mutation or volume cleanup.
USAGE
}

IMAGE_REF=""
KEEP_NEWEST=5

while (( $# > 0 )); do
  case "$1" in
    --image)
      IMAGE_REF="${2:-}"
      shift 2
      ;;
    --keep-newest)
      KEEP_NEWEST="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 2
      ;;
  esac
done

if [[ ! "$IMAGE_REF" =~ ^(.+)@sha256:([0-9a-fA-F]{64})$ ]]; then
  echo "--image must be an immutable image digest reference (@sha256:...)" >&2
  exit 2
fi

REPO="${BASH_REMATCH[1]}"

if [[ ! "$KEEP_NEWEST" =~ ^[0-9]+$ ]] \
  || (( KEEP_NEWEST < 2 || KEEP_NEWEST > 20 )); then
  echo "--keep-newest must be an integer between 2 and 20" >&2
  exit 2
fi

command -v docker >/dev/null 2>&1 || {
  echo "Docker CLI is not available" >&2
  exit 2
}

docker info >/dev/null 2>&1 || {
  echo "Docker daemon is not available" >&2
  exit 2
}

REQUESTED_ID="$(
  docker image inspect \
    --format '{{.Id}}' \
    "$IMAGE_REF"
)"
REQUESTED_ID="${REQUESTED_ID#sha256:}"

INV="$(mktemp)"
KEEP="$(mktemp)"
DELETE="$(mktemp)"

cleanup() {
  rm -f "$INV" "$KEEP" "$DELETE"
}
trap cleanup EXIT

echo "DutyLog image retention repository: $REPO"
echo "Requested image: ${REQUESTED_ID:0:12}"
echo "Newest images retained: $KEEP_NEWEST"

docker image ls -a \
  --no-trunc \
  --format '{{.Repository}}|{{.ID}}' \
| awk -F'|' -v repo="$REPO" '
    $1 == repo {
      sub(/^sha256:/, "", $2)
      print $2
    }
  ' \
| sort -u \
| while IFS= read -r id; do
    [[ -n "$id" ]] || continue

    created="$(
      docker image inspect \
        --format '{{.Created}}' \
        "$id"
    )"

    epoch="$(
      date -d "$created" +%s
    )"

    printf '%s|%s|%s\n' \
      "$epoch" \
      "$id" \
      "$created"
  done \
| sort -t'|' -k1,1nr \
> "$INV"

TOTAL="$(
  wc -l < "$INV" \
  | tr -d ' '
)"

echo "Repository images before retention: $TOTAL"

# The exact image requested by this deployment is always protected.
printf '%s\n' "$REQUESTED_ID" > "$KEEP"

# Protect every image referenced by every container, including stopped
# containers. This also makes a shared staging/production host safe.
docker ps -aq \
| while IFS= read -r cid; do
    [[ -n "$cid" ]] || continue

    docker inspect \
      --format '{{.Image}}' \
      "$cid"
  done \
| sed 's/^sha256://' \
>> "$KEEP"

# Keep a bounded recent rollback window in addition to all container-owned
# images. On an ordinary deployment the requested image is also the newest.
head -n "$KEEP_NEWEST" "$INV" \
| cut -d'|' -f2 \
>> "$KEEP"

sort -u -o "$KEEP" "$KEEP"

cut -d'|' -f2 "$INV" \
| grep -vxFf "$KEEP" \
> "$DELETE" \
|| true

CANDIDATES="$(
  wc -l < "$DELETE" \
  | tr -d ' '
)"

echo "Retention delete candidates: $CANDIDATES"

DELETED=0
BLOCKED=0

while IFS= read -r id; do
  [[ -n "$id" ]] || continue

  refs="$(
    docker image inspect \
      --format '{{range .RepoDigests}}{{.}}{{println}}{{end}}' \
      "$id" \
    | awk -v prefix="${REPO}@sha256:" '
        index($0, prefix) == 1 {
          print
        }
      '
  )"

  if [[ -z "$refs" ]]; then
    echo "No removable repository digest reference for ${id:0:12}" >&2
    BLOCKED=$((BLOCKED + 1))
    continue
  fi

  image_ok=true

  while IFS= read -r ref; do
    [[ -n "$ref" ]] || continue

    echo "Removing old DutyLog image reference: $ref"

    if ! docker image rm "$ref"; then
      image_ok=false
    fi
  done <<< "$refs"

  if [[ "$image_ok" == true ]]; then
    DELETED=$((DELETED + 1))
  else
    BLOCKED=$((BLOCKED + 1))
  fi
done < "$DELETE"

# The just-deployed immutable image must still exist after retention.
docker image inspect "$IMAGE_REF" >/dev/null

echo "Retention deleted images: $DELETED"
echo "Retention blocked images: $BLOCKED"

if (( BLOCKED > 0 )); then
  echo "DutyLog image retention did not complete cleanly" >&2
  exit 3
fi

echo "DutyLog image retention completed."
