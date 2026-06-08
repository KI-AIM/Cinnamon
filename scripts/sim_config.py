from dataclasses import dataclass, field
from typing import List, Optional
import yaml

@dataclass
class Profile:
    """
    Represents a profile containing configuration details.

    This class holds information necessary for configuring and managing a profile.

    Attributes:
        name (str): The name of the profile. Is always required.
        mode (str): The mode in which the profile operates. Allowed values are `single_execution` and `directory_execution`. Is always required.
        cinnamon_url (str): The URL to Cinnamon. Is always required.
        config_file (str): The path to Cinnamon's configuration file. Is always required.
        data_file (str | None): An optional path to the data file. Is ignored in `directory_execution` mode. Can be None if Cinnamon fetches the data.
        source_directory (str | None): The path to the source directory. Is required in `directory_execution` and ignored in `single_execution` mode.
        result_directory (str | None): The path to the result directory. Required in `single_execution` mode. Optional in `directory_execution` mode. If not provided, results will be stored in the source directory.
    """
    name: str
    mode: str
    cinnamon_url: str
    config_file: str
    data_file: str | None = None
    source_directory: str | None = None
    result_directory: str | None = None

@dataclass
class SimConfig:
    active_profile_name: str
    profiles: List[Profile] = field(default_factory=list)

    @property
    def active_profile(self) -> Optional[Profile]:
        for profile in self.profiles:
            if profile.name == self.active_profile_name:
                return profile
        return None

    @classmethod
    def from_yaml(cls, path: str) -> 'SimConfig':
        with open(path, 'r') as file:
            data = yaml.safe_load(file)

        profiles = [Profile(**p) for p in data.get('profiles', [])]
        return cls(active_profile_name=data.get('active_profile'), profiles=profiles)
