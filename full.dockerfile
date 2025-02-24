FROM python:3.12-rc-slim-bookworm as requirements-stage
WORKDIR /tmp
RUN pip install poetry && poetry self add poetry-plugin-export
COPY ./pyproject.toml ./poetry.lock* /tmp/
RUN poetry export -f requirements.txt --output requirements.txt --without-hashes
# Second stage, copy over the requirements and install them
FROM python:3.12-rc-slim-bookworm
RUN apt update && apt install -y libzbar0
WORKDIR /app
COPY --from=requirements-stage /tmp/requirements.txt /app/requirements.txt
RUN apt-get update && apt-get install -y build-essential
RUN pip install --no-cache-dir --upgrade -r /app/requirements.txt
COPY ./copilot /app/copilot
COPY ./tools /app/tools
COPY ./run.py /app/run.py
COPY ./tools_config.json /app/tools_config.json
COPY ./tools_deps.toml  /app/tools_deps.toml
COPY README.md /app/README.md
CMD python run.py