# Second stage, copy over the requirements and install them
FROM python:3.12.9-slim
RUN apt update && apt install -y libzbar0 curl
# Install uv
RUN curl -Ls https://astral.sh/uv/install.sh | sh
# Add uv to PATH
ENV PATH="/root/.local/bin:$PATH"
# Create virtual environment outside of /app
RUN mkdir -p /venv && cd /venv && uv venv
WORKDIR /app
# Entrypoint: install dependencies + run script
CMD ["sh", "-c", ". /venv/.venv/bin/activate && uv pip install --link-mode=copy -r requirements.txt && python run.py"]
