from dataclasses import dataclass, field
from typing import List, Optional
import yaml

@dataclass
class Profile:
    name: str
    mode: str
    cinnamon_url: str
    config_file: str
    data_file: str | None = None

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
