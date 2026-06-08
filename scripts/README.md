# Script for serving the Cinnamon workflow API

## Scheduled execution on Windows

The `directory_execution` mode is used to continuously anonaymize datasets that are stored in the source directory.
To run this script on Windows, you can create a scheduled task that executes the script at regular intervals.
Here are the steps to set up a scheduled task:

1) Open Task Scheduler
2) Click on "Create Basic Task" in the right-hand pane
3) Follow the prompts to name your task and set the trigger (e.g., daily, weekly, etc.)
4) In the "Action" step, select "Start a program"
5) In the "Program/script" field, enter the path to your Python executable (e.g., `C:\Python39\python.exe`)
6) In the "Add arguments" field, enter the path to your script (e.g., `C:\path\to\your\script.py`)
7) In the "Start in" field, enter the directory where your script is located (e.g., `C:\path\to\your`)
8) Click "Finish" to create the task
