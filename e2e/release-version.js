const { version } = require('../package.json');

if (!/^\d+\.\d+\.\d+$/.test(version)) {
  throw new Error(`Invalid DutyLog release version in package.json: ${version}`);
}

module.exports = Object.freeze({ releaseVersion: version });
