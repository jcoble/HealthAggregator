.PHONY: install run test install-launchd uninstall-launchd

VENV := .venv
PYTHON := $(VENV)/bin/python
PIP := $(VENV)/bin/pip
PYTEST := $(VENV)/bin/pytest
UVICORN := $(VENV)/bin/uvicorn

install:
	python3 -m venv $(VENV)
	$(PIP) install --upgrade pip
	$(PIP) install -r requirements.txt

run:
	$(UVICORN) daemon:app --host 0.0.0.0 --port 8719

test:
	$(PYTEST) -v tests/

install-launchd:
	@echo "(Phase 7 will wire this up.)"

uninstall-launchd:
	@echo "(Phase 7 will wire this up.)"
