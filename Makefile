.EXPORT_ALL_VARIABLES:
.PHONY: venv install pre-commit clean

setup: venv install pre-commit

install: ${LOCAL_PYTHON}
	echo "Installing dependencies..."
	poetry install --no-root --sync

pre-commit: ${LOCAL_PYTHON} ${LOCAL_PRE_COMMIT}
	echo "Setting up pre-commit..."
	pre-commit install
	pre-commit autoupdate

export:
	echo "Exporting ..."
	poetry export -f requirements.txt --output requirements.txt
