FROM etendo/etendo_copilot_base:1.0.0

WORKDIR /app-deps

COPY ./tools_deps.toml ./requirements.txt ./local_setup.py ./

RUN uv pip install -r requirements.txt && \
    python local_setup.py && \
    apt-get purge -y --auto-remove build-essential && \
    rm -rf /root/.cache/uv

WORKDIR /app
RUN mkdir -p /checkpoints vectordbs

COPY . .

CMD ["python", "run.py"]
