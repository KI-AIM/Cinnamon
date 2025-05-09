"""Top-level package for DeepEcho."""

__author__ = 'MIT Data To AI Lab'
__email__ = 'dailabmit@gmail.com'
__version__ = '0.3.0.post1'

__path__ = __import__('pkgutil').extend_path(__path__, __name__)

from synthetic_longitudinal_data_generator.deepecho.models.par import PARModel

__all__ = [
    'load_demo',
    'BasicGANModel',
    'PARModel',
]
