# Setup
Set the environment variable `OPENAI_API_KEY` with a valid value.

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

As tool for managing multiple Python versions you can use [pyenv](https://github.com/pyenv/pyenv). `pyenv` does not manage package dependencies, so for this purpose you can use [Poetry](https://python-poetry.org/). It will create an isolated virtual environment for the `etendo-copilot-core` project.

* Install `pyenv`: https://github.com/pyenv/pyenv#installation
* Install `Poetry`: `curl -sSL https://install.python-poetry.org | python3 -`
* Create `etendo-copilot-core` venv using poetry (recommended approach):

```
pyenv install 3.10
pyenv local 3.10
poetry env use 3.10
poetry install
```

Alternative, you can use Docker.

### Torch and MacOS issue
If you are getting this issue from `poetry install`: `Unable to find installation candidates for torch (2.0.1+cpu)`.

Workaround:
```
poetry shell
pip install torch==2.0.1
deactivate
```

Verify installation:
```
poetry run python
>>> import torch
>>> torch.__version__
'2.0.1'
```

### Add new dependencies
* For prod dependency run: `poetry add <dep_name>`
* For dev dependency run: `poetry add <dep_name> --group dev`

## How to run copilot
* Locally outside docker:
	- Copy `.env.sample` into `.env` and set the right values
	- `poetry run python run.py`

* Using docker, make sure `.env` is created and all the variables are set, only then run `docker run --env-file .env -p 5001:5001 etendo/chatbot_etendo`. You can set the port that you want, just be sure to set the same port in the image from `.env` if not, the api will never be reached.

* Mount code as volume: `docker run --env-file .env -p 5001:5001 -v $(pwd)/copilot:/app/copilot etendo/chatbot_etendo`.

## How to run unit test
`poetry run pytest tests`

## Pre-commit
* Install pre-commit from [HERE](https://pre-commit.com/#install)
* Setup pre-commit `pre-commit install & pre-commit autoupdate`
* If you want to run for all the files: `pre-commit run --all-files`
