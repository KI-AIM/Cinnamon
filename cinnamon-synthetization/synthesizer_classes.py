from synthetic_tabular_data_generator.algorithms.ctgan import CtganSynthesizer
from synthetic_tabular_data_generator.algorithms.tvae import TvaeSynthesizer
from synthetic_tabular_data_generator.algorithms.ddpm import DdpmSynthesizer
#from synthetic_longitudinal_data_generator.algorithms.par import ParSynthesizer
from synthetic_tabular_data_generator.algorithms.bayesian_network import BayesianNetworkSynthesizer
from synthetic_tabular_data_generator.algorithms.arf import AdversarialRandomForestsSynthesizer
from synthetic_tabular_data_generator.algorithms.rtvae import RtvaeSynthesizer
from synthetic_tabular_data_generator.algorithms.ollama_tabular import OllamaTabularSynthesizer

synthesizer_classes = {
    'ctgan': {
        'version': '0.1',
        'type': 'cross-sectional',
        'class': CtganSynthesizer,
        'display_name': 'Conditional Tabular GAN',
        'description': 'Conditional Tabular GAN (CTGAN) are a specialized type of generative model designed to create realistic synthetic tabular data, mimicking the statistical properties of original datasets. Leveraging a generator-discriminator framework, CTGANs learn the relationships within your data and generate new rows conditioned on specified parameters, enabling the creation of synthetic datasets that preserve privacy while retaining key analytical insights. This approach is particularly valuable for scenarios requiring data augmentation or secure model development.',
        'URL': '/synthetic_tabular_data_generator/synthesizer_config/ctgan.yaml'
    },
    'tvae': {
        'version': '0.1',
        'type': 'cross-sectional',
        'class': TvaeSynthesizer,
        'display_name': 'Tabular Variational Autoencoder',
        'description': 'Tabular Variational Autoencoder (TVAE) are a specialized type of generative model designed to create realistic synthetic tabular data, mimicking the statistical properties of original datasets. Leveraging an encoder-decoder architecture, TVAEs learn compressed latent representations of your data and generate new rows by sampling from this latent space, enabling the creation of synthetic datasets that preserve privacy while retaining key analytical insights. TVAE is especially effective for controlled simulations and what-if analysis where smooth plausible data variations are explored.',
        'URL': '/synthetic_tabular_data_generator/synthesizer_config/tvae.yaml'
    },
    # 'par': {
    #     'version': '0.1',
    #     'type': 'longitudinal',
    #     'class': ParSynthesizer,
    #     'display_name': 'Probabilistic Auto-Regressive Model',
    #     'description': 'PAR is a model that can generate synthetic longitudinal data.',
    #     'URL': '/synthetic_longitudinal_data_generator/synthesizer_config/par.yaml'
    # },
    'bayesian_network': {
        'version': '0.1',
        'type': 'cross-sectional',
        'class': BayesianNetworkSynthesizer,
        'display_name': 'Bayesian Network',
        'description': 'Bayesian Networks are a specialized type of probabilistic graphical model designed to create realistic synthetic tabular data, mimicking the statistical properties of original datasets. Leveraging a directed acyclic graph of conditional dependencies, these models learn relationships among columns and generate new rows via sampling conditioned on specified parameters, enabling the creation of synthetic datasets that preserve privacy while retaining key analytical insights. This makes them a strong choice when interpretability and constraint handling across mixed discrete and continuous variables are important.',
        'URL': '/synthetic_tabular_data_generator/synthesizer_config/bayesian_network.yaml'
    },
    'arf': {
        'version': '0.1',
        'type': 'cross-sectional',
        'class': AdversarialRandomForestsSynthesizer,
        'display_name': 'Adversarial Random Forest',
        'description': 'Adversarial Random Forests (ARF) are a specialized type of generative model designed to create realistic synthetic tabular data, mimicking the statistical properties of original datasets. Leveraging an ensemble of decision trees trained within an adversarial framework, ARF learns fine-grained relationships in your data and generates new rows through iterative refinement, enabling the creation of synthetic datasets that preserve privacy while retaining key analytical insights. ARF works well for capturing complex, non-linear interactions with modest tuning effort and serves as a robust baseline for benchmarking synthetic data quality.',
        'URL': '/synthetic_tabular_data_generator/synthesizer_config/arf.yaml'
    },
    'ddpm': {
        'version': '0.1',
        'type': 'cross-sectional',
        'class': DdpmSynthesizer,
        'display_name': 'TabDDPM (Denoising Diffusion Probablistic Models)',
        'description': 'A diffusion-based model for high-fidelity tabular data generation (synthcity TabDDPM).',
        'URL': '/synthetic_tabular_data_generator/synthesizer_config/ddpm.yaml'
    },
    'rtvae': {
        'version': '0.1',
        'type': 'cross-sectional',
        'class': RtvaeSynthesizer,
        'display_name': 'Robust Tabular Variational Autoencoder',
        'description': 'An implementation of Robust Variational Autoencoder for data generation using beta divergence learning.',
        'URL': '/synthetic_tabular_data_generator/synthesizer_config/rtvae.yaml'
    },
    'ollama_tabular': {
        'version': '0.1',
        'type': 'cross-sectional',
        'class': OllamaTabularSynthesizer,
        'display_name': 'Ollama LLM Tabular Synthesizer',
        'description': 'A local LLM-based synthesizer for tabular data generation via Ollama.',
        'URL': '/synthetic_tabular_data_generator/synthesizer_config/ollama_tabular.yaml'
    }
}
