"""
Prompt Logger Module

This module provides functionality to log LLM prompts and responses for evaluation and debugging purposes.
All prompts sent to the LLM and their corresponding responses are saved to text files in the outputs/prompts directory.
"""

import os
import uuid
from datetime import datetime
from pathlib import Path
from typing import Optional


class PromptLogger:
    """Logger for LLM prompts and responses."""
    
    _instance: Optional["PromptLogger"] = None
    _session_id: Optional[str] = None
    _log_dir: Optional[Path] = None
    
    def __init__(self):
        self._session_id = None
        self._log_dir = None
        self._current_file_handle = None
        self._current_file_path = None
    
    @classmethod
    def get_instance(cls) -> "PromptLogger":
        """Get the singleton instance of PromptLogger."""
        if cls._instance is None:
            cls._instance = PromptLogger()
        return cls._instance
    
    @classmethod
    def reset(cls):
        """Reset the singleton instance (useful for testing)."""
        if cls._instance is not None:
            if cls._instance._current_file_handle is not None:
                cls._instance._current_file_handle.close()
        cls._instance = None
        cls._session_id = None
        cls._log_dir = None
    
    def initialize(self, session_key: Optional[str] = None):
        """
        Initialize the prompt logger with a session key.
        
        Args:
            session_key: Optional session key to organize logs by session.
        """
        self._session_id = session_key or str(uuid.uuid4())
        
        # Create log directory
        base_dir = Path(__file__).parent.parent.parent / "outputs" / "prompts"
        base_dir.mkdir(parents=True, exist_ok=True)
        
        if self._session_id:
            self._log_dir = base_dir / self._session_id
            self._log_dir.mkdir(parents=True, exist_ok=True)
        else:
            self._log_dir = base_dir / "default"
            self._log_dir.mkdir(parents=True, exist_ok=True)
        
        print(f"[PromptLogger] Initialized for session: {self._session_id}")
    
    def log_prompt_and_response(
        self,
        prompt: str,
        response: str,
        system_prompt: Optional[str] = None,
        metadata: Optional[dict] = None,
    ) -> str:
        """
        Log a prompt and its corresponding response.
        
        Args:
            prompt: The user prompt sent to the LLM.
            response: The response received from the LLM.
            system_prompt: Optional system prompt.
            metadata: Optional metadata (e.g., model name, temperature, etc.).
        
        Returns:
            The path to the log file.
        """
        if self._log_dir is None:
            self.initialize()
        
        # Generate unique filename
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        unique_id = uuid.uuid4().hex[:8]
        filename = f"prompt_{timestamp}_{unique_id}.txt"
        file_path = self._log_dir / filename
        
        # Build log content
        log_content = self._build_log_content(prompt, response, system_prompt, metadata)
        
        # Write to file
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(log_content)
        
        print(f"[PromptLogger] Logged: {file_path}")
        return str(file_path)
    
    def _build_log_content(
        self,
        prompt: str,
        response: str,
        system_prompt: Optional[str] = None,
        metadata: Optional[dict] = None,
    ) -> str:
        """Build the log file content."""
        lines = []
        
        # Header
        lines.append("=" * 80)
        lines.append("LLM PROMPT AND RESPONSE LOG")
        lines.append("=" * 80)
        lines.append(f"Timestamp: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        lines.append(f"Session ID: {self._session_id}")
        lines.append("")
        
        # Metadata
        if metadata:
            lines.append("METADATA:")
            lines.append("-" * 40)
            for key, value in metadata.items():
                lines.append(f"{key}: {value}")
            lines.append("")
        
        # System prompt (if provided)
        if system_prompt:
            lines.append("SYSTEM PROMPT:")
            lines.append("-" * 40)
            lines.append(system_prompt)
            lines.append("")
        
        # User prompt
        lines.append("USER PROMPT:")
        lines.append("-" * 40)
        lines.append(prompt)
        lines.append("")
        
        # Response
        lines.append("LLM RESPONSE:")
        lines.append("-" * 40)
        lines.append(response)
        lines.append("")
        
        lines.append("=" * 80)
        
        return "\n".join(lines)
    
    def get_log_directory(self) -> Optional[Path]:
        """Get the current log directory."""
        return self._log_dir


# Global function for easy access
def log_llm_interaction(
    prompt: str,
    response: str,
    system_prompt: Optional[str] = None,
    metadata: Optional[dict] = None,
    session_key: Optional[str] = None,
) -> str:
    """
    Convenience function to log an LLM interaction.
    
    Args:
        prompt: The user prompt.
        response: The LLM response.
        system_prompt: Optional system prompt.
        metadata: Optional metadata.
        session_key: Optional session key.
    
    Returns:
        The path to the log file.
    """
    logger = PromptLogger.get_instance()
    if logger._log_dir is None and session_key:
        logger.initialize(session_key)
    elif logger._log_dir is None:
        logger.initialize()
    
    return logger.log_prompt_and_response(prompt, response, system_prompt, metadata)
