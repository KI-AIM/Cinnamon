import os
import yaml
import sys


def initialize_status_file(file_path, session_key, synthesizer_name):
    """
    Create and initialize a YAML file to track the status of a process.

    This function sets up a status file with predefined steps and their default states,
    including initialization, fitting, sampling, and callback stages.

    Args:
        file_path (str): The file path where the YAML status file will be created.
        session_key (str): A unique identifier for the session.
        synthesizer_name (str): The name of the synthesizer being tracked.

    Returns:
        None
    """
    yaml_status = {
        "session_key": str(session_key),
        "synthesizer_name": str(synthesizer_name),
        "status": [
            {
                "step": "initialization",
                "duration": "Waiting",
                "completed": "False"
            },
            {
                "step": "fitting",
                "duration": "Waiting",
                "completed": "False",
                "remaining_time": "Waiting",
            },
            {
                "step": "sampling",
                "duration": "Waiting",
                "completed": "False",
                "remaining_time": "Waiting",
            },
            {
                "step": "callback",
                "completed": "False"
            }
        ]
    }

    os.makedirs(os.path.dirname(file_path), exist_ok=True)

    with open(file_path, 'w') as f:
        yaml.dump(yaml_status, f, allow_unicode=True, default_flow_style=False)


def update_status(file_name, step, duration=None, completed=None, remaining_time=None):
    """
    Update the status of a specific step in the YAML status file.

    This function modifies the status file to update the duration, completion status,
    and remaining time for a specified step.

    Args:
        file_name (str): The file path of the YAML status file to be updated.
        step (str): The name of the step to update (e.g., "fitting" or "sampling").
        duration (str, optional): The duration to set for the step.
        completed (str, optional): The completion status to set for the step.
        remaining_time (str, optional): The estimated remaining time to set for the step.

    Returns:
        None
    """
    # Read the current YAML file to get the latest status
    with open(file_name, 'r') as f:
        data = yaml.safe_load(f)

    # Find the specific step in the status list
    for status_step in data['status']:
        if status_step['step'] == step:
            # Update the duration if a value is provided
            if duration is not None:
                status_step['duration'] = str(duration)

            # Update the completion status if a value is provided
            if completed is not None:
                status_step['completed'] = str(completed)

            # Update the remaining time if a value is provided and the field exists
            if 'remaining_time' in status_step and remaining_time is not None:
                status_step['remaining_time'] = str(remaining_time)
            break

    # Write the updated data back to the file
    with open(file_name, 'w') as f:
        yaml.dump(data, f, allow_unicode=True, default_flow_style=False)


class InterceptStdOut:
    """
    A class to intercept and process stdout messages during a specific process stage.

    This class captures standard output messages, extracts relevant information
    (e.g., remaining time), and updates a corresponding YAML status file.

    Args:
        file_name (str): The file path of the YAML status file to update.
        process_stage (str): The name of the process stage being monitored
        (e.g., "fitting" or "sampling").
    """
    def __init__(self, file_name, process_stage):
        self.terminal = sys.stdout
        self.file_name = file_name
        self.process_stage = process_stage  # 'fitting' or 'sampling'

    def write(self, message):
        """
        Write a message to both the terminal and the YAML status file.

        If the message contains "Estimated remaining time:", the remaining time is
        extracted and the YAML file is updated.

        Args:
            message (str): The message to write.

        Returns:
            None
        """
        self.terminal.write(message)
        if "Estimated remaining time:" in message:
            # Extract the remaining time from the message
            remaining_time = message.split("Estimated remaining time:")[1].strip().split(" ")[0]
            self.update_yaml_file(remaining_time)

    def flush(self):
        """
        Flush the stdout buffer.

        This ensures all buffered messages are written out.

        Args:
            None

        Returns:
            None
        """
        self.terminal.flush()

    def update_yaml_file(self, remaining_time):
        """
        Update the remaining time for the monitored process stage in the YAML status file.

        Args:
            remaining_time (str): The estimated remaining time to set in the YAML file.

        Returns:
            None
        """
        # Read the current YAML file
        with open(self.file_name, 'r') as f:
            data = yaml.safe_load(f)

        # Find the specific step in the status list and update it
        for status_step in data['status']:
            if status_step['step'] == self.process_stage:
                status_step['remaining_time'] = remaining_time
                break

        # Write the updated data back to the file
        with open(self.file_name, 'w') as f:
            yaml.dump(data, f, allow_unicode=True, default_flow_style=False)

    def close(self):
        """
        Flush the stdout buffer and ensure all resources are released.

        Args:
            None

        Returns:
            None
        """
        self.flush()  # Ensure all buffers are flushed
