function parseDotEnv(text) {
  const values = {};

  for (const line of text.split(/\r?\n/)) {
    const trimmed = line.trim();

    if (!trimmed || trimmed.startsWith("#")) {
      continue;
    }

    const index = trimmed.indexOf("=");

    if (index < 0) {
      continue;
    }

    const key = trimmed.slice(0, index).trim();
    let value = trimmed.slice(index + 1).trim();

    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }

    values[key] = value;
  }

  return values;
}

function loadDotEnv() {
  const paths = __ENV.K6_ENV_FILE
    ? [__ENV.K6_ENV_FILE]
    : [
        "../.env",
        "./.env",
        "k6/.env",
        "../../k6/.env",
        "../../.env",
      ];

  for (const path of paths) {
    try {
      return parseDotEnv(open(path));
    } catch (e) {
      // Try the next conventional path.
    }
  }

  return {};
}

const dotEnv = loadDotEnv();

export function env(name) {
  const value = __ENV[name] || dotEnv[name];

  if (value === undefined || value === null || value === "") {
    throw new Error(`${name} is required. Set ${name} or k6/.env ${name}.`);
  }

  return value;
}

export function optionalEnv(name) {
  return __ENV[name] || dotEnv[name] || "";
}

export function passwordOrEnv(password) {
  return password || env("PASSWORD");
}
