from __future__ import annotations

import datetime as dt
import json
import logging
import logging.config
import logging.handlers
import pathlib
import sys
import warnings

LOG_RECORD_BUILTIN_ATTRS = {
    "args",
    "asctime",
    "created",
    "exc_info",
    "exc_text",
    "filename",
    "funcName",
    "levelname",
    "levelno",
    "lineno",
    "module",
    "msecs",
    "message",
    "msg",
    "name",
    "pathname",
    "process",
    "processName",
    "relativeCreated",
    "stack_info",
    "thread",
    "threadName",
    "taskName",
}


def setup_logging():
    """
    Set up logging configuration for the application using a JSON configuration file.

    This method configures the logging behavior of the application by loading
    a logging configuration from a specified JSON file. It also sets up
    custom handlers for uncaught exceptions and warnings to ensure they are
    appropriately logged.

    Args:
        None

    Returns:
        None
    """
    config_file = pathlib.Path("api_utility/logging/log_config.json")
    with open(config_file) as f_in:
        config = json.load(f_in)
    logging.config.dictConfig(config)

    def handle_exception(exc_type, exc_value, exc_traceback):
        """
        Handle uncaught exceptions and log them appropriately.

        Args:
            exc_type (type): Exception type.
            exc_value (Exception): Exception instance.
            exc_traceback: Traceback object associated with the exception.

        Returns:
            None
        """
        if issubclass(exc_type, KeyboardInterrupt):
            sys.__excepthook__(exc_type, exc_value, exc_traceback)
            return
        logging.getLogger().error("Uncaught exception", exc_info=True)

    sys.excepthook = handle_exception

    def handle_warning(message, category, filename, lineno, line=None):
        """
        Handle warnings and log them appropriately.

        Args:
            message (str): The warning message.
            category (Type[Warning]): The category of the warning.
            filename (str): The name of the file where the warning occurred.
            lineno (int): The line number where the warning occurred.
            line (Optional[str]): The line of code triggering the warning (default is None).

        Returns:
            None
        """
        log_message = warnings.formatwarning(message, category, filename, lineno, line)
        logging.warning(log_message)

    warnings.showwarning = handle_warning


class MyJSONFormatter(logging.Formatter):
    """
    A custom logging formatter that formats log records as JSON strings.

    This formatter allows for flexible customization of log record fields using
    a mapping (`fmt_keys`) that specifies how log record attributes should be
    mapped to JSON keys. It also includes additional fields like the log message
    and a timestamp.

    Args:
        fmt_keys (dict[str, str] | None, optional): A dictionary mapping JSON keys
        to log record attributes. If not provided, default mappings will be used.
    """
    def __init__(
            self,
            *,
            fmt_keys: dict[str, str] | None = None,
    ):
        super().__init__()
        self.fmt_keys = fmt_keys if fmt_keys is not None else {}
    """
    Initialize the JSON formatter with optional custom field mappings.

    Args:
        fmt_keys (dict[str, str] | None, optional): A dictionary specifying the 
        mapping of JSON keys to log record attributes. If not provided, a default 
        empty dictionary is used.
    """

    def format(self, record: logging.LogRecord) -> str:
        """
        Format the specified log record as a JSON string.

        This method converts a log record into a JSON-formatted string, using the
        custom mappings defined in `fmt_keys` and adding standard fields like the
        message and timestamp.

        Args:
            record (logging.LogRecord): The log record to be formatted.

        Returns:
            str: The JSON-formatted log message.
        """
        message = self._prepare_log_dict(record)
        return json.dumps(message, default=str)

    def _prepare_log_dict(self, record: logging.LogRecord):
        """
        Prepare a dictionary representation of the log record for JSON serialization.

        This method constructs a dictionary by combining custom mappings defined in
        `fmt_keys` with standard fields like the log message and timestamp. It also
        includes user-defined fields from the log record.

        Args:
            record (logging.LogRecord): The log record to be processed.

        Returns:
            dict: A dictionary containing log record information formatted for JSON output.
        """
        always_fields = {
            "message": record.getMessage(),
            "timestamp": dt.datetime.fromtimestamp(
                record.created, tz=dt.timezone.utc
            ).isoformat(),
        }

        message = {
            key: msg_val
            if (msg_val := always_fields.pop(val, None)) is not None
            else getattr(record, val)
            for key, val in self.fmt_keys.items()
        }
        message.update(always_fields)

        for key, val in record.__dict__.items():
            if key not in LOG_RECORD_BUILTIN_ATTRS:
                message[key] = val

        return message
