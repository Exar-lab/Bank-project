import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OPENAPI_FILE = ROOT / "docs" / "api" / "openapi.yaml"
POSTMAN_FILE = ROOT / "docs" / "postman" / "banco-service.postman_collection.json"
DIAGRAMS_DIR = ROOT / "docs" / "diagrams"


class DocsConsistencyTest(unittest.TestCase):
    EXPECTED_ENDPOINTS = 66
    EXPECTED_DIAGRAMS = {
        "account-lifecycle.md",
        "auth-flow.md",
        "card-lifecycle.md",
        "envelope-workflow.md",
        "transaction-transfer-flow.md",
    }

    def test_operation_ids_unique_and_complete(self):
        text = OPENAPI_FILE.read_text(encoding="utf-8")
        operation_ids = re.findall(r"^\s*operationId:\s*([^\s#]+)", text, flags=re.MULTILINE)

        self.assertEqual(
            self.EXPECTED_ENDPOINTS,
            len(operation_ids),
            "OpenAPI operationId total must match documented endpoint count",
        )
        self.assertEqual(
            len(operation_ids),
            len(set(operation_ids)),
            "operationId values must be unique",
        )

    def test_openapi_postman_parity(self):
        text = OPENAPI_FILE.read_text(encoding="utf-8")
        openapi_endpoints = self._parse_openapi_endpoints(text)
        postman_endpoints = self._parse_postman_endpoints()

        missing_in_postman = sorted(openapi_endpoints - postman_endpoints)
        extra_in_postman = sorted(postman_endpoints - openapi_endpoints)

        self.assertEqual(
            self.EXPECTED_ENDPOINTS,
            len(openapi_endpoints),
            "OpenAPI parsed endpoint total must be deterministic",
        )
        self.assertEqual(
            self.EXPECTED_ENDPOINTS,
            len(postman_endpoints),
            "Postman parsed endpoint total must be deterministic",
        )
        self.assertEqual([], missing_in_postman, f"Missing in Postman: {missing_in_postman}")
        self.assertEqual([], extra_in_postman, f"Extra in Postman: {extra_in_postman}")

    def test_mermaid_blocks_exist_for_required_diagrams(self):
        discovered = {p.name for p in DIAGRAMS_DIR.glob("*.md")}
        missing_required = self.EXPECTED_DIAGRAMS - discovered
        self.assertEqual(set(), missing_required, f"Missing required diagrams: {sorted(missing_required)}")

        for diagram in sorted(self.EXPECTED_DIAGRAMS):
            content = (DIAGRAMS_DIR / diagram).read_text(encoding="utf-8")
            mermaid_blocks = len(re.findall(r"```mermaid", content))
            self.assertGreaterEqual(mermaid_blocks, 1, f"{diagram} must contain at least one Mermaid block")

    @staticmethod
    def _normalize_openapi_path(path: str) -> str:
        return re.sub(r"\{[^}]+\}", "{}", path)

    @staticmethod
    def _normalize_postman_path(raw: str) -> str:
        value = raw.replace("{{baseUrl}}", "").split("?", 1)[0]
        if "://" in value:
            value = "/" + value.split("/", 3)[-1]
        if not value.startswith("/"):
            value = "/" + value

        # Postman path vars: /resource/{{id}}
        value = re.sub(r"\{\{[^}]+\}\}", "{}", value)
        # OpenAPI path params: /resource/{id}
        value = re.sub(r"\{[^}]+\}", "{}", value)
        # Express-like vars: /resource/:id
        value = re.sub(r":[A-Za-z0-9_]+", "{}", value)
        return value

    def _parse_openapi_endpoints(self, text: str) -> set[tuple[str, str]]:
        current_path = None
        in_paths = False
        endpoints: set[tuple[str, str]] = set()

        for line in text.splitlines():
            if re.match(r"^paths:\s*$", line):
                in_paths = True
                continue

            if in_paths and re.match(r"^[^\s].*:\s*$", line):
                break

            if not in_paths:
                continue

            path_match = re.match(r"^\s{2}(/[^:]+):\s*$", line)
            if path_match:
                current_path = path_match.group(1)
                continue

            method_match = re.match(r"^\s{4}(get|post|put|patch|delete|options|head):\s*$", line)
            if method_match and current_path:
                endpoints.add((method_match.group(1).upper(), self._normalize_openapi_path(current_path)))

        return endpoints

    def _parse_postman_endpoints(self) -> set[tuple[str, str]]:
        collection = json.loads(POSTMAN_FILE.read_text(encoding="utf-8"))
        endpoints: set[tuple[str, str]] = set()

        def walk(items: list[dict]):
            for item in items:
                if "request" in item:
                    request = item["request"]
                    method = str(request.get("method", "GET")).upper()
                    url = request.get("url", {})
                    raw = url.get("raw", "") if isinstance(url, dict) else str(url)
                    endpoints.add((method, self._normalize_postman_path(raw)))
                if "item" in item:
                    walk(item["item"])

        walk(collection.get("item", []))
        return endpoints


if __name__ == "__main__":
    unittest.main(verbosity=2)
