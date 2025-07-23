.EXPORT_ALL_VARIABLES:
.PHONY: venv install pre-commit clean

setup: venv install pre-commit

install: ${LOCAL_PYTHON}
	echo "Installing dependencies..."
	RUN curl -Ls https://astral.sh/uv/install.sh | sh


pre-commit: ${LOCAL_PYTHON} ${LOCAL_PRE_COMMIT}
	echo "Setting up pre-commit..."
	pre-commit install
	pre-commit autoupdate

export:
	echo "Exporting ..."
	uv export --no-hashes --format=requirements-txt --output-file=requirements.txt
