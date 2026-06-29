export function parseLoginUserCsv(text, defaultPassword) {
  const input = text.replace(/^\uFEFF/, "");
  const firstLine = input.split(/\r?\n/, 1)[0] || "";
  const delimiter =
    (firstLine.match(/\t/g) || []).length > (firstLine.match(/,/g) || []).length
      ? "\t"
      : ",";
  const rows = parseDelimited(input, delimiter);

  if (rows.length < 2) {
    throw new Error("login user CSV must contain a header and at least one row");
  }

  const headers = rows[0].map((header) => header.trim());
  const headerIndex = Object.fromEntries(
    headers.map((header, index) => [header, index]),
  );

  if (!("email" in headerIndex)) {
    throw new Error("login user CSV missing required column: email");
  }

  return rows.slice(1).map((row, rowIndex) => {
    const line = rowIndex + 2;
    const email = value(row, headerIndex, "email");
    const password =
      "password" in headerIndex && value(row, headerIndex, "password")
        ? value(row, headerIndex, "password")
        : defaultPassword;

    if (!email) {
      throw new Error(`login user CSV line ${line}: email is empty`);
    }
    if (!password) {
      throw new Error(`login user CSV line ${line}: password is empty`);
    }

    return { email, password };
  });
}

function value(row, headerIndex, name) {
  return (row[headerIndex[name]] || "").trim();
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
