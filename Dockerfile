FROM python:3.10-slim-buster as requirements-stage
WORKDIR /tmp
RUN pip install poetry
COPY ./pyproject.toml ./poetry.lock* /tmp/
RUN poetry export -f requirements.txt --output requirements.txt --without-hashes
# Second stage, copy over the requirements and install them
FROM python:3.10-slim-buster
RUN apt update && apt install -y libzbar0
WORKDIR /app
COPY --from=requirements-stage /tmp/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir --upgrade -r /app/requirements.txt
COPY ./copilot /app/copilot
COPY ./tools /app/tools
COPY ./run.py /app/run.py
COPY ./tools_config.json /app/tools_config.json
COPY README.md /app/README.md
CMD python run.py