export function parseCouponIssueCandidateCsv(text) {
  const rows = parseCsvRows(text);
  const headerIndex = buildHeaderIndex(rows[0]);

  requireColumns(headerIndex, ["email", "couponEventId"]);

  return rows.slice(1).map((row, rowIndex) => {
    const line = rowIndex + 2;
    const candidate = {
      email: requiredString(row, headerIndex, "email", line),
      couponEventId: positiveInteger(
        requiredString(row, headerIndex, "couponEventId", line),
        line,
        "couponEventId",
      ),
    };

    copyOptionalString(candidate, row, headerIndex, "password");
    copyOptionalPositiveInteger(candidate, row, headerIndex, "userId", line);

    return candidate;
  });
}

export function parseOrderCouponCandidateCsv(text) {
  const rows = parseCsvRows(text);
  const headerIndex = buildHeaderIndex(rows[0]);

  requireColumns(headerIndex, ["email", "cartItemId", "productId", "orderQuantity"]);

  return rows.slice(1).map((row, rowIndex) => {
    const line = rowIndex + 2;
    const candidate = {
      email: requiredString(row, headerIndex, "email", line),
      cartItemId: positiveInteger(requiredString(row, headerIndex, "cartItemId", line), line, "cartItemId"),
      productId: positiveInteger(requiredString(row, headerIndex, "productId", line), line, "productId"),
      orderQuantity: positiveInteger(
        requiredString(row, headerIndex, "orderQuantity", line),
        line,
        "orderQuantity",
      ),
    };

    copyOptionalString(candidate, row, headerIndex, "password");
    copyOptionalPositiveInteger(candidate, row, headerIndex, "userId", line);
    copyOptionalPositiveInteger(candidate, row, headerIndex, "couponEventId", line);
    copyOptionalPositiveInteger(candidate, row, headerIndex, "couponIssueId", line);

    if ("cancelAfterOrder" in headerIndex) {
      candidate.cancelAfterOrder = parseBoolean(optionalString(row, headerIndex, "cancelAfterOrder"));
    }

    return candidate;
  });
}

function parseCsvRows(text) {
  const input = text.replace(/^\uFEFF/, "");
  const firstLine = input.split(/\r?\n/, 1)[0] || "";
  const delimiter =
    (firstLine.match(/\t/g) || []).length > (firstLine.match(/,/g) || []).length
      ? "\t"
      : ",";
  const rows = parseDelimited(input, delimiter);

  if (rows.length < 2) {
    throw new Error("candidate CSV must contain a header and at least one row");
  }

  return rows;
}

function buildHeaderIndex(headerRow) {
  return Object.fromEntries(headerRow.map((header, index) => [header.trim(), index]));
}

function requireColumns(headerIndex, columns) {
  for (const column of columns) {
    if (!(column in headerIndex)) {
      throw new Error(`candidate CSV missing required column: ${column}`);
    }
  }
}

function requiredString(row, headerIndex, name, line) {
  const raw = optionalString(row, headerIndex, name);
  if (!raw) {
    throw new Error(`candidate CSV line ${line}: ${name} is empty`);
  }
  return raw;
}

function optionalString(row, headerIndex, name) {
  if (!(name in headerIndex)) {
    return "";
  }
  return (row[headerIndex[name]] || "").trim();
}

function copyOptionalString(candidate, row, headerIndex, name) {
  const raw = optionalString(row, headerIndex, name);
  if (raw) {
    candidate[name] = raw;
  }
}

function copyOptionalPositiveInteger(candidate, row, headerIndex, name, line) {
  const raw = optionalString(row, headerIndex, name);
  if (raw) {
    candidate[name] = positiveInteger(raw, line, name);
  }
}

function positiveInteger(raw, line, name) {
  if (!/^\d+$/.test(raw)) {
    throw new Error(`candidate CSV line ${line}: ${name} must be a positive integer`);
  }

  const parsed = Number(raw);
  if (!Number.isInteger(parsed) || parsed <= 0) {
    throw new Error(`candidate CSV line ${line}: ${name} must be a positive integer`);
  }

  return parsed;
}

function parseBoolean(raw) {
  if (!raw) {
    return false;
  }
  return ["1", "true", "yes", "y"].includes(raw.toLowerCase());
}

function parseDelimited(input, delimiter) {
  const rows = [];
  let row = [];
  let field = "";
  let inQuotes = false;

  for (let i = 0; i < input.length; i += 1) {
    const char = input[i];
    const next = input[i + 1];

    if (char === '"') {
      if (inQuotes && next === '"') {
        field += '"';
        i += 1;
      } else {
        inQuotes = !inQuotes;
      }
      continue;
    }

    if (char === delimiter && !inQuotes) {
      row.push(field);
      field = "";
      continue;
    }

    if ((char === "\n" || char === "\r") && !inQuotes) {
      if (char === "\r" && next === "\n") {
        i += 1;
      }
      row.push(field);
      if (row.some((value) => value.length > 0)) {
        rows.push(row);
      }
      row = [];
      field = "";
      continue;
    }

    field += char;
  }

  if (field.length > 0 || row.length > 0) {
    row.push(field);
    if (row.some((value) => value.length > 0)) {
      rows.push(row);
    }
  }

  return rows;
}
