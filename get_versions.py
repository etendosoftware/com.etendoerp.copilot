import urllib.request
import urllib.error
import json
import time
import argparse
import re
from pathlib import Path


DEFAULT_PACKAGES = [
    'langchain',
    'langchain-openai',
    'langchain-google-genai',
    'langchain-community',
    'langgraph',
    'langchain-core',
    'langsmith',
    'langchain-experimental',
    'langchain-chroma',
    'langchain-anthropic',
    'langchain-ollama',
    'langchain-text-splitters',
    'langgraph-supervisor',
    'langgraph-checkpoint',
    'langgraph-checkpoint-sqlite',
    'langchain-deepseek',
    'langchain-sandbox',
    'langchain-mcp-adapters'
]


def get_latest_version(pkg_name, timeout=10):
    url = f'https://pypi.org/pypi/{pkg_name}/json'
    req = urllib.request.Request(url, headers={
        'User-Agent': 'get_versions/1.0 (+https://github.com)'
    })
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            data = json.loads(response.read().decode())
            return data.get('info', {}).get('version')
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return f'Not found (404)'
        return f'HTTP Error {e.code}'
    except Exception as e:
        return f'Error - {e}'


def parse_pyproject_packages(pyproject_path: Path):
    if not pyproject_path.exists():
        raise FileNotFoundError(f'{pyproject_path} not found')
    text = pyproject_path.read_text()
    # Simple regex to capture dependency lines inside the dependencies = [ ... ] block
    deps_block = re.search(r"dependencies\s*=\s*\[([\s\S]*?)\]\n", text)
    if not deps_block:
        return []
    deps_text = deps_block.group(1)
    pkg_names = []
    for line in deps_text.splitlines():
        line = line.strip().strip(',').strip()
        if not line or line.startswith('#'):
            continue
        # remove markers and version specs
        # examples: "pkg>=1.0,<2" or "pkg~=1.2 ; python_version..."
        # extract the package name up to first [<>=~! ;]
        m = re.match(r'"?([A-Za-z0-9_.-]+)', line)
        if m:
            pkg_names.append(m.group(1))
    return pkg_names


def main(all_packages: bool, output: Path | None):
    if all_packages:
        pkgs = parse_pyproject_packages(Path('pyproject.toml'))
    else:
        pkgs = DEFAULT_PACKAGES

    lines = []
    for pkg in pkgs:
        version = get_latest_version(pkg)
        line = f'{pkg}: {version}'
        print(line)
        lines.append(line)
        time.sleep(0.08)

    if output:
        output.write_text('\n'.join(lines) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Get latest PyPI versions for packages')
    parser.add_argument('--all', action='store_true', help='Read packages from pyproject.toml')
    parser.add_argument('--output', '-o', type=Path, help='Write output to file')
    args = parser.parse_args()
    try:
        main(args.all, args.output)
    except Exception as e:
        print('Error:', e)
        raise