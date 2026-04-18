.PHONY: install run test install-launchd uninstall-launchd

VENV := .venv
PYTHON := $(VENV)/bin/python
PIP := $(VENV)/bin/pip
PYTEST := $(VENV)/bin/pytest
UVICORN := $(VENV)/bin/uvicorn

PLIST_NAME := com.healthaggregator.syncd.plist
PLIST_DEST := $(HOME)/Library/LaunchAgents/$(PLIST_NAME)
DAEMON_DIR := $(shell pwd)

install:
	python3 -m venv $(VENV)
	$(PIP) install --upgrade pip
	$(PIP) install -r requirements.txt

run:
	$(UVICORN) daemon:app --host 0.0.0.0 --port 8719

test:
	$(PYTEST) -v tests/

install-launchd:
	@mkdir -p $(HOME)/Library/LaunchAgents
	@sed \
	  -e 's|__DAEMON_DIR__|$(DAEMON_DIR)|g' \
	  -e 's|__HOME__|$(HOME)|g' \
	  launchd/$(PLIST_NAME) > $(PLIST_DEST)
	@launchctl unload $(PLIST_DEST) 2>/dev/null || true
	@launchctl load $(PLIST_DEST)
	@echo "Installed $(PLIST_DEST). Daemon is now running on port 8719 and will auto-start on login."

uninstall-launchd:
	@launchctl unload $(PLIST_DEST) 2>/dev/null || true
	@rm -f $(PLIST_DEST)
	@echo "Uninstalled $(PLIST_DEST)."
