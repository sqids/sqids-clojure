import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

const [requestFilePath, specDir] = process.argv.slice(2);

if (!requestFilePath || !specDir) {
  throw new Error("Expected request JSON path and sqids-spec checkout path");
}

const { default: Sqids } = await import(
  pathToFileURL(`${specDir}/src/index.ts`).href
);

const requests = JSON.parse(readFileSync(requestFilePath, "utf8"));

const wireToOptions = (wireOptions = {}) => {
  const options = {};

  if ("alphabet" in wireOptions) {
    options.alphabet = wireOptions.alphabet;
  }

  if ("min-length" in wireOptions) {
    options.minLength = wireOptions["min-length"];
  }

  if ("block-list" in wireOptions) {
    options.blocklist = new Set(wireOptions["block-list"]);
  }

  return options;
};

const responseFor = (request, value) => ({
  id: request.id,
  op: request.op,
  status: "ok",
  value,
});

const errorFor = (request, error) => ({
  id: request.id,
  op: request.op,
  status: "error",
  message: error?.message ?? String(error),
});

const evaluate = (request) => {
  try {
    const sqids = new Sqids(wireToOptions(request.options));

    switch (request.op) {
      case "roundtrip": {
        const sqid = sqids.encode(request.numbers);

        return responseFor(request, {
          sqid,
          numbers: sqids.decode(sqid),
        });
      }

      case "decode":
        return responseFor(request, sqids.decode(request.sqid));

      case "encode":
        return responseFor(request, sqids.encode(request.numbers));

      case "sqids":
        return responseFor(request, "initialized");

      default:
        throw new Error(`Unknown parity op: ${request.op}`);
    }
  } catch (error) {
    return errorFor(request, error);
  }
};

console.log(JSON.stringify(requests.map(evaluate)));
