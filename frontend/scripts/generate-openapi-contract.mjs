import { createHash } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const sourcePath = fileURLToPath(new URL("../../src/main/resources/static/openapi/dutylog-v1.yaml", import.meta.url));
const outputPath = fileURLToPath(new URL("../src/generated/dutylog-api.ts", import.meta.url));
const checkOnly = process.argv.includes("--check");

const source = readFileSync(sourcePath, "utf8").replace(/\r\n/g, "\n");
const lines = source.split("\n");
const sha256 = createHash("sha256").update(source).digest("hex");

function splitInline(value) {
  const result = [];
  let current = "";
  let depth = 0;
  let quote = null;
  for (const character of value) {
    if (quote) {
      current += character;
      if (character === quote) quote = null;
      continue;
    }
    if (character === "'" || character === '"') {
      quote = character;
      current += character;
      continue;
    }
    if (character === "[" || character === "{") depth += 1;
    if (character === "]" || character === "}") depth -= 1;
    if (character === "," && depth === 0) {
      result.push(current.trim());
      current = "";
    } else {
      current += character;
    }
  }
  if (current.trim()) result.push(current.trim());
  return result;
}

function inlineMap(value) {
  const trimmed = value.trim();
  if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return new Map();
  const map = new Map();
  for (const part of splitInline(trimmed.slice(1, -1))) {
    const colon = part.indexOf(":");
    if (colon > 0) map.set(part.slice(0, colon).trim(), part.slice(colon + 1).trim());
  }
  return map;
}

function unquote(value) {
  const trimmed = String(value ?? "").trim();
  if ((trimmed.startsWith("'") && trimmed.endsWith("'")) || (trimmed.startsWith('"') && trimmed.endsWith('"'))) {
    return trimmed.slice(1, -1);
  }
  return trimmed;
}

function refName(value) {
  const match = String(value ?? "").match(/#\/components\/schemas\/([A-Za-z0-9_]+)/);
  return match?.[1] ?? null;
}

function enumType(value) {
  const trimmed = String(value ?? "").trim();
  if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return null;
  const values = splitInline(trimmed.slice(1, -1)).map(unquote).filter(Boolean);
  if (!values.length) return null;
  return values.map(item => {
    if (item === "true" || item === "false" || /^-?\d+(?:\.\d+)?$/.test(item)) return item;
    return JSON.stringify(item);
  }).join(" | ");
}

function scalarType(type) {
  switch (unquote(type)) {
    case "string": return "string";
    case "integer":
    case "number": return "number";
    case "boolean": return "boolean";
    case "object": return "Record<string, unknown>";
    default: return "unknown";
  }
}

function propertyType(block) {
  const first = block[0]?.trim() ?? "";
  const inline = first.includes(":") ? first.slice(first.indexOf(":") + 1).trim() : "";
  const map = inlineMap(inline);
  const joined = block.map(line => line.trim()).join("\n");
  const nullable = map.get("nullable") === "true" || /(?:^|\n)nullable:\s*true(?:$|\n)/.test(joined);
  let type;

  const inlineEnum = enumType(map.get("enum"));
  const blockEnumMatch = joined.match(/(?:^|\n)enum:\s*(\[[^\n]+\])/);
  const blockEnum = enumType(blockEnumMatch?.[1]);
  const inlineType = unquote(map.get("type"));
  const blockType = joined.match(/(?:^|\n)type:\s*([^\n]+)/)?.[1]?.trim();
  const rawType = inlineType || blockType || "unknown";

  if (inlineEnum || blockEnum) {
    type = inlineEnum ?? blockEnum;
  } else if (rawType === "array") {
    // A $ref nested below items describes the array element, not the
    // property itself. Resolve array shape before considering direct refs.
    const itemInline = map.get("items");
    const itemMap = inlineMap(itemInline ?? "");
    const itemRef = refName(itemMap.get("$ref")) ?? refName(joined.match(/items:[\s\S]*/)?.[0]);
    const itemEnum = enumType(itemMap.get("enum")) ?? enumType(joined.match(/items:\s*\{[^}]*enum:\s*(\[[^\]]+\])/)?.[1]);
    const itemType = itemRef ? `DutyLogApiSchemas.${itemRef}` : itemEnum ?? scalarType(itemMap.get("type") ?? joined.match(/items:\s*\{[^}]*type:\s*([^,}]+)/)?.[1]);
    type = `Array<${itemType}>`;
  } else if (rawType === "object") {
    const additionalRef = refName(map.get("additionalProperties")) ?? refName(joined.match(/additionalProperties:[\s\S]*/)?.[0]);
    const additionalInline = inlineMap(map.get("additionalProperties") ?? "");
    const additionalType = additionalRef
      ? `DutyLogApiSchemas.${additionalRef}`
      : scalarType(additionalInline.get("type") ?? joined.match(/additionalProperties:\s*\{[^}]*type:\s*([^,}]+)/)?.[1]);
    type = `Record<string, ${additionalType}>`;
  } else {
    const directRef = refName(map.get("$ref")) ?? (rawType === "unknown" ? refName(joined) : null);
    type = directRef ? `DutyLogApiSchemas.${directRef}` : scalarType(rawType);
  }
  return nullable ? `${type} | null` : type;
}

function schemaBlocks() {
  const start = lines.findIndex(line => line === "  schemas:");
  if (start < 0) throw new Error("OpenAPI components.schemas section is missing");
  const blocks = new Map();
  let current = null;
  for (let index = start + 1; index < lines.length; index += 1) {
    const line = lines[index];
    const match = line.match(/^    ([A-Za-z0-9_]+):\s*$/);
    if (match) {
      current = match[1];
      blocks.set(current, []);
      continue;
    }
    if (current) blocks.get(current).push(line);
  }
  return blocks;
}

function renderSchema(name, block) {
  const requiredLine = block.find(line => /^      required:\s*\[/.test(line) || /^          required:\s*\[/.test(line));
  const required = new Set(requiredLine ? splitInline(requiredLine.slice(requiredLine.indexOf("[") + 1, requiredLine.lastIndexOf("]"))).map(unquote) : []);
  const allOfStart = block.findIndex(line => line === "      allOf:");
  const allOfRefs = [];
  if (allOfStart >= 0) {
    for (let index = allOfStart + 1; index < block.length; index += 1) {
      const line = block[index];
      if (line.trim() && lineIndent(line) <= 6) break;
      const match = line.match(/^        - \$ref:\s*(.+)$/);
      const ref = match ? refName(match[1]) : null;
      if (ref && !allOfRefs.includes(ref)) allOfRefs.push(ref);
    }
  }
  const propertyStart = block.findIndex(line => line === "      properties:" || /^          properties:$/.test(line));
  const properties = [];
  if (propertyStart >= 0) {
    const baseIndent = lineIndent(block[propertyStart]);
    let current = null;
    let currentLines = [];
    const flush = () => {
      if (current) properties.push({ name: current, block: currentLines });
      current = null;
      currentLines = [];
    };
    for (let index = propertyStart + 1; index < block.length; index += 1) {
      const line = block[index];
      const indent = lineIndent(line);
      if (line.trim() && indent <= baseIndent) break;
      const match = line.match(/^\s+([A-Za-z0-9_-]+):(?:\s*(.*))?$/);
      if (match && indent === baseIndent + 2) {
        flush();
        current = match[1];
        currentLines = [`${current}: ${match[2] ?? ""}`];
      } else if (current) {
        currentLines.push(line);
      }
    }
    flush();
  }

  if (!properties.length && allOfRefs.length) {
    return `  export type ${name} = ${allOfRefs.map(ref => `DutyLogApiSchemas.${ref}`).join(" & ")};`;
  }
  if (!properties.length) return `  export type ${name} = unknown;`;

  const extendsType = allOfRefs.filter(ref => ref !== name).map(ref => `DutyLogApiSchemas.${ref}`).join(" & ");
  const members = properties.map(property => {
    const key = /^[A-Za-z_$][A-Za-z0-9_$]*$/.test(property.name) ? property.name : JSON.stringify(property.name);
    return `    ${key}${required.has(property.name) ? "" : "?"}: ${propertyType(property.block)};`;
  }).join("\n");
  const object = `{\n${members}\n  }`;
  return `  export type ${name} = ${extendsType ? `${extendsType} & ` : ""}${object};`;
}

function lineIndent(line) {
  return line.match(/^ */)?.[0].length ?? 0;
}

function schemaTypeFromLines(schemaLines) {
  if (!schemaLines.length) return null;
  const first = schemaLines[0].trim();
  const inline = first.startsWith("schema:") ? first.slice("schema:".length).trim() : first;
  const map = inlineMap(inline);
  const joined = schemaLines.map(line => line.trim()).join("\n");
  const ref = refName(map.get("$ref")) ?? refName(joined);
  const inlineEnum = enumType(map.get("enum"));
  const blockEnum = enumType(joined.match(/(?:^|\n)enum:\s*(\[[^\n]+\])/)?.[1]);
  if (inlineEnum || blockEnum) return inlineEnum ?? blockEnum;

  const inlineType = unquote(map.get("type"));
  const blockType = joined.match(/(?:^|\n)type:\s*([^\n]+)/)?.[1]?.trim();
  const rawType = inlineType || blockType || (ref ? "$ref" : "unknown");

  if (rawType === "$ref" && ref) return `DutyLogApiSchemas.${ref}`;
  if (rawType === "array") {
    const itemsSource = map.get("items") ?? joined.match(/(?:^|\n)items:\s*(.*)$/m)?.[1] ?? "";
    const itemsMap = inlineMap(itemsSource);
    const itemRef = refName(itemsMap.get("$ref")) ?? refName(joined.match(/(?:^|\n)items:[\s\S]*/)?.[0]);
    const itemEnum = enumType(itemsMap.get("enum"))
      ?? enumType(joined.match(/items:\s*\{[^}]*enum:\s*(\[[^\]]+\])/)?.[1]);
    const itemType = itemRef
      ? `DutyLogApiSchemas.${itemRef}`
      : itemEnum ?? scalarType(itemsMap.get("type") ?? joined.match(/items:\s*\{[^}]*type:\s*([^,}]+)/)?.[1]);
    return `Array<${itemType}>`;
  }
  if (rawType === "object") {
    const requiredLine = schemaLines.find(line => /required:\s*\[/.test(line));
    const required = new Set(requiredLine
      ? splitInline(requiredLine.slice(requiredLine.indexOf("[") + 1, requiredLine.lastIndexOf("]"))).map(unquote)
      : []);
    const propertyStart = schemaLines.findIndex(line => /^\s*properties:\s*$/.test(line));
    const properties = [];
    if (propertyStart >= 0) {
      const baseIndent = lineIndent(schemaLines[propertyStart]);
      let current = null;
      let currentLines = [];
      const flush = () => {
        if (current) properties.push({ name: current, block: currentLines });
        current = null;
        currentLines = [];
      };
      for (let index = propertyStart + 1; index < schemaLines.length; index += 1) {
        const line = schemaLines[index];
        const indent = lineIndent(line);
        if (line.trim() && indent <= baseIndent) break;
        const match = line.match(/^\s+([A-Za-z0-9_-]+):(?:\s*(.*))?$/);
        if (match && indent === baseIndent + 2) {
          flush();
          current = match[1];
          currentLines = [`${current}: ${match[2] ?? ""}`];
        } else if (current) {
          currentLines.push(line);
        }
      }
      flush();
    }
    if (properties.length) {
      return `{\n${properties.map(property => {
        const key = /^[A-Za-z_$][A-Za-z0-9_$]*$/.test(property.name) ? property.name : JSON.stringify(property.name);
        return `      ${key}${required.has(property.name) ? "" : "?"}: ${propertyType(property.block)};`;
      }).join("\n")}\n    }`;
    }
    const additionalRef = refName(map.get("additionalProperties")) ?? refName(joined.match(/additionalProperties:[\s\S]*/)?.[0]);
    const additionalMap = inlineMap(map.get("additionalProperties") ?? "");
    const additionalType = additionalRef
      ? `DutyLogApiSchemas.${additionalRef}`
      : scalarType(additionalMap.get("type") ?? joined.match(/additionalProperties:\s*\{[^}]*type:\s*([^,}]+)/)?.[1]);
    return `Record<string, ${additionalType}>`;
  }
  return ref ? `DutyLogApiSchemas.${ref}` : scalarType(rawType);
}

function extractSchema(block, fromIndex, toIndex = block.length) {
  for (let index = fromIndex; index < toIndex; index += 1) {
    const line = block[index];
    if (!/^\s*schema:\s*/.test(line)) continue;
    const indent = lineIndent(line);
    const result = [line.trimStart()];
    if (line.slice(line.indexOf("schema:") + "schema:".length).trim()) return result;
    for (let nested = index + 1; nested < toIndex; nested += 1) {
      const candidate = block[nested];
      if (candidate.trim() && lineIndent(candidate) <= indent) break;
      result.push(candidate);
    }
    return result;
  }
  return [];
}

function operationBlocks() {
  const methods = new Set(["get", "post", "put", "patch", "delete", "head", "options"]);
  const operations = [];
  let path = null;
  let method = null;
  let block = [];
  const flush = () => {
    if (!path || !method || !block.length) return;
    const operationId = block.map(line => line.match(/^      operationId:\s*([A-Za-z0-9_]+)/)?.[1]).find(Boolean);
    if (!operationId) return;

    const requestStart = block.findIndex(line => line === "      requestBody:");
    const responsesStart = block.findIndex(line => line === "      responses:");
    const requestSchema = requestStart >= 0
      ? extractSchema(block, requestStart + 1, responsesStart >= 0 ? responsesStart : block.length)
      : [];
    const requestType = schemaTypeFromLines(requestSchema) ?? "undefined";

    let responseType = "unknown";
    let responseStatus = null;
    if (responsesStart >= 0) {
      for (let index = responsesStart + 1; index < block.length; index += 1) {
        const status = block[index].match(/^        ['"]?(2\d\d)['"]?:/);
        if (!status) continue;
        responseStatus = status[1];
        let end = block.length;
        for (let nested = index + 1; nested < block.length; nested += 1) {
          if (/^        ['"]?\d{3}['"]?:/.test(block[nested])) {
            end = nested;
            break;
          }
        }
        responseType = schemaTypeFromLines(extractSchema(block, index + 1, end))
          ?? (responseStatus === "204" ? "undefined" : "unknown");
        break;
      }
    }

    operations.push({ operationId, path, method: method.toUpperCase(), requestType, responseType, responseStatus });
  };

  for (const line of lines) {
    if (line === "components:") break;
    const pathMatch = line.match(/^  (\/api\/[^:]+):\s*$/);
    if (pathMatch) {
      flush();
      path = pathMatch[1];
      method = null;
      block = [];
      continue;
    }
    const methodMatch = line.match(/^    ([a-z]+):\s*$/);
    if (path && methodMatch && methods.has(methodMatch[1])) {
      flush();
      method = methodMatch[1];
      block = [];
      continue;
    }
    if (path && method) block.push(line);
  }
  flush();
  return operations.sort((left, right) => left.operationId.localeCompare(right.operationId));
}

const schemas = [...schemaBlocks().entries()].sort(([left], [right]) => left.localeCompare(right));
const operations = operationBlocks();
if (!operations.length) throw new Error("OpenAPI operationId entries are missing");

const generated = `/* eslint-disable */
/**
 * GENERATED FILE — DO NOT EDIT.
 * Source: src/main/resources/static/openapi/dutylog-v1.yaml
 * SHA-256: ${sha256}
 * Generator: frontend/scripts/generate-openapi-contract.mjs
 * Contract: ${operations.length} operations, ${schemas.length} schemas
 */

export const DUTYLOG_OPENAPI_SOURCE_SHA256 = ${JSON.stringify(sha256)};

export namespace DutyLogApiSchemas {
${schemas.map(([name, block]) => renderSchema(name, block)).join("\n\n")}
}

export const dutyLogOperations = {
${operations.map(operation => `  ${JSON.stringify(operation.operationId)}: { method: ${JSON.stringify(operation.method)}, path: ${JSON.stringify(operation.path)} },`).join("\n")}
} as const;

export type DutyLogOperationId = keyof typeof dutyLogOperations;

export interface DutyLogOperationTypes {
${operations.map(operation => `  ${JSON.stringify(operation.operationId)}: {\n    requestBody: ${operation.requestType};\n    response: ${operation.responseType};\n  };`).join("\n")}
}

export type DutyLogOperationRequest<T extends DutyLogOperationId> = DutyLogOperationTypes[T]["requestBody"];
export type DutyLogOperationResponse<T extends DutyLogOperationId> = DutyLogOperationTypes[T]["response"];
`;

if (checkOnly) {
  let current = "";
  try { current = readFileSync(outputPath, "utf8").replace(/\r\n/g, "\n"); } catch {}
  if (current !== generated) {
    console.error("Generated OpenAPI TypeScript contract is stale. Run: npm --prefix frontend run contract:generate");
    process.exit(1);
  }
  console.log(`OpenAPI contract drift check passed (${operations.length} operations, ${schemas.length} schemas, ${sha256.slice(0, 12)}).`);
} else {
  writeFileSync(outputPath, generated, "utf8");
  console.log(`Generated ${outputPath} from ${operations.length} operations and ${schemas.length} schemas.`);
}
