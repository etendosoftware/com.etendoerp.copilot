"""This is the core module of the Copilot application."""

from setuptools import find_packages, setup

setup(
    name="copilot",
    version="0.1",
    packages=find_packages(),
    install_requires=["flask_wtf"],
    author="Sebastian Barrozo",
    author_email="sebastian.barrozo@etendo.software",
    description="This is the core module of the Copilot application.",
    license="MIT",
)
