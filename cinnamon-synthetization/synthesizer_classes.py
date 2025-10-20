from synthetic_tabular_data_generator.algorithms.ctgan import CtganSynthesizer
from synthetic_tabular_data_generator.algorithms.tvae import TvaeSynthesizer
#from synthetic_longitudinal_data_generator.algorithms.par import ParSynthesizer
from synthetic_tabular_data_generator.algorithms.bayesian_network import BayesianNetworkSynthesizer
from synthetic_tabular_data_generator.algorithms.arf import AdversarialRandomForestsSynthesizer

synthesizer_classes = {
    'ctgan': {
        'version': '0.1',
        'type': 'cross-sectional',
        'class': CtganSynthesizer,
        'display_name': 'Conditional Tabular GAN',
        'description': 'CTGAN is a GAN-based model that can generate synthetic tabular data.',
        'URL': '/synthetic_tabular_data_generator/synthesizer_config/ctgan.yaml'
    },
    'tvae': {
        'version': '0.1',
        'type': 'cross-sectional',
        'class': TvaeSynthesizer,
        'display_name': 'Tabular Variational Autoencoder',
        'description': 'TVAE is a VAE-based model that can generate synthetic tabular data.',
        'URL': '/synthetic_tabular_data_generator/synthesizer_config/tvae.yaml'
    },
    #'par': {
    #    'version': '0.1',
    #    'type': 'longitudinal',
    #    'class': ParSynthesizer,
    #    'display_name': 'Probabilistic Auto-Regressive Model',
    #    'description': 'PAR is a model that can generate synthetic longitudinal data.',
    #    'URL': '/synthetic_longitudinal_data_generator/synthesizer_config/par.yaml'
    #},
    'bayesian_network': {
        'version': '0.1',
        'type': 'cross-sectional',
        'class': BayesianNetworkSynthesizer,
        'display_name': 'Bayesian Network',
        'description': 'A probabilistic graphical model that represents a set of variables and their conditional dependencies.',
        'URL': '/synthetic_tabular_data_generator/synthesizer_config/bayesian_network.yaml'
    },
    'arf': {
        'version': '0.1',
        'type': 'cross-sectional',
        'class': AdversarialRandomForestsSynthesizer,
        'display_name': 'Adversarial Random Forest',
        'description': 'A model that combines random forests with adversarial learning to improve data synthesis.',
        'URL': '/synthetic_tabular_data_generator/synthesizer_config/arf.yaml'
    },
        'ddpm': {
        'version': '0.1',
        'type': 'cross-sectional',
        'class': DdpmSynthesizer,
        'display_name': 'TabDDPM (Denoising Diffusion Probablistic Models)',
        'description': 'A diffusion-based model for high-fidelity tabular data generation (synthcity TabDDPM).',
        'URL': '/synthetic_tabular_data_generator/synthesizer_config/ddpm.yaml'
    }
}
