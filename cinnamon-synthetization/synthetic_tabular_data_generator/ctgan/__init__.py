# -*- coding: utf-8 -*-

"""Top-level package for ctgan."""

__author__ = 'MIT Data To AI Lab'
__email__ = 'dailabmit@gmail.com'
__version__ = '0.6.0'


from synthetic_tabular_data_generator.ctgan.synthesizers.ctgan import CTGAN
from synthetic_tabular_data_generator.ctgan.synthesizers.tvae import TVAE

__all__ = (
    'CTGAN',
    'TVAE',
)
