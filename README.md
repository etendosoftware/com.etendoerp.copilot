# Setup
Set the environment variable `OPENAI_API_KEY` with a valid value

# Deploy docker image
```
docker build -t etendo/chatbot_etendo .
docker push etendo/chatbot_etendo
```

# FRONT
```
kubectl port-forward -n chat-etendo svc/das 8092:8092
kubectl port-forward -n chat-etendo svc/etendo-retrieval 8085:8080
```

# Backend Development

## Virtual environment

As tool for managing multiple Python versions you can use [pyenv](https://github.com/pyenv/pyenv). `pyenv` does not manage package dependencies, so for this purpose you can use [Poetry](https://python-poetry.org/).
It will create an isolated virtual environment for the `etendo-copilot-core` project.

* Install `pyenv`: https://github.com/pyenv/pyenv#installation
* Install `Poetry`: `curl -sSL https://install.python-poetry.org | python3 -`
* Create `etendo-copilot-core` venv using poetry (recommended approach):

```
pyenv install 3.10
pyenv local 3.10
poetry env use 3.10
poetry install
```

## How to run copilot

* Locally outside docker:
	- Copy `.env.sample` into `.env` and set the right values
	- `poetry run python run.py`

* Using docker: `docker run -e RUN_MODE='<run_mode>' -e OPENAI_API_KEY="<api-key-value>" -p 5001:5000 etendo/chatbot_etendo`


## Pre-commit
* Install pre-commit from [HERE](https://pre-commit.com/#install)
* Setup pre-commit `pre-commit install & pre-commit autoupdate`
* If you want to run for all the files: `pre-commit run --all-files`
