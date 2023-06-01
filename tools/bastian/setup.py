"""Este es un ejemplo de paquete."""

from setuptools import setup, find_packages

setup(
    name="mi_paquete",
    version="0.1",
    packages=find_packages(),
    install_requires=["requests", "numpy>=1.10"],
    author="Tu Nombre",
    author_email="tuemail@example.com",
    description="Este es un ejemplo de paquete",
    license="MIT",
    keywords="ejemplo tutorial",
)
