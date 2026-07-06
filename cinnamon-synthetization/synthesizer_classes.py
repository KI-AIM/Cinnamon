from synthetic_tabular_data_generator.algorithms.arf import AdversarialRandomForestsSynthesizer
from synthetic_tabular_data_generator.algorithms.bayesian_network import BayesianNetworkSynthesizer
from synthetic_tabular_data_generator.algorithms.ctgan import CtganSynthesizer
from synthetic_tabular_data_generator.algorithms.ddpm import DdpmSynthesizer
from synthetic_tabular_data_generator.algorithms.llm_nearest_neighbor_few_shot_text_synthesis import (
    LlmNearestNeighborFewShotTextSynthesisSynthesizer,
)
from synthetic_tabular_data_generator.algorithms.llm_nearest_neighbor_knowledge_grounded_text_synthesis import (
    LlmNearestNeighborKnowledgeGroundedTextSynthesisSynthesizer,
)
from synthetic_tabular_data_generator.algorithms.llm_text_only_paraphrase_synthesis import (
    LlmTextOnlyParaphraseSynthesisSynthesizer,
)
from synthetic_tabular_data_generator.algorithms.llm_text_only_indirect_identifier_rewrite_synthesis import (
    LlmTextOnlyIndirectIdentifierRewriteSynthesisSynthesizer,
)
from synthetic_tabular_data_generator.algorithms.llm_text_only_semantic_variation_synthesis import (
    LlmTextOnlySemanticVariationSynthesisSynthesizer,
)
from synthetic_tabular_data_generator.algorithms.rtvae import RtvaeSynthesizer
from synthetic_tabular_data_generator.algorithms.tvae import TvaeSynthesizer

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
        'description': 'TabDDPM is a diffusion-based generative model designed to create high-fidelity synthetic tabular data by gradually denoising random noise into realistic data samples. Leveraging denoising diffusion probabilistic modeling, it learns complex feature distributions and dependencies in the original dataset, enabling the generation of synthetic rows that closely resemble real-world data patterns. This approach is particularly useful for datasets with intricate relationships between numerical and categorical variables. TabDDPM can be a strong choice when synthetic data quality and distributional similarity are prioritized over training speed.',
        'URL': '/synthetic_tabular_data_generator/synthesizer_config/ddpm.yaml'
    },
    'rtvae': {
        'version': '0.1',
        'type': 'cross-sectional',
        'class': RtvaeSynthesizer,
        'display_name': 'Robust Tabular Variational Autoencoder',
        'description': 'Robust Tabular Variational Autoencoder (RTVAE) is a generative model designed to create realistic synthetic tabular data while being more resilient to noise and outliers in the original dataset. Leveraging a variational autoencoder architecture with beta divergence learning, RTVAE learns robust latent representations and generates new rows by sampling from this learned latent space. This makes the model especially useful for datasets that contain imperfect, noisy, or partially inconsistent records. RTVAE helps preserve important statistical structures while reducing the influence of extreme or unreliable observations.',
        'URL': '/synthetic_tabular_data_generator/synthesizer_config/rtvae.yaml'
    },
    'llm_nearest_neighbor_few_shot_text_synthesis': {
        'version': '0.1',
        'type': 'cross-sectional',
        'class': LlmNearestNeighborFewShotTextSynthesisSynthesizer,
        'display_name': 'LLM Nearest-neighbor Few-shot Text Synthesis',
        'description': 'LLM Nearest-neighbor Few-shot Text Synthesis is a language-model-based synthesizer designed to generate realistic text fields for synthetic tabular rows while using original data as semantic context. Leveraging large language models, it creates text values that align with the surrounding structured columns and can correct inconsistent structured values when necessary. This approach is especially useful for datasets that combine tabular attributes with free-text descriptions, comments, reports, or notes. It helps maintain semantic coherence between generated text and structured data, improving the usability of synthetic datasets for downstream analysis or testing. The structured data is not created by this model',
        'URL': '/synthetic_tabular_data_generator/synthesizer_config/llm_nearest_neighbor_few_shot_text_synthesis.yaml'
    },
    'llm_nearest_neighbor_knowledge_grounded_text_synthesis': {
        'version': '0.1',
        'type': 'cross-sectional',
        'class': LlmNearestNeighborKnowledgeGroundedTextSynthesisSynthesizer,
        'display_name': 'LLM Knowledge-grounded Text Synthesis',
        'description': 'Generate synthetic medical free-text fields using an LLM with dynamically selected similar original examples. Knowledge-base grounding is not implemented yet and is currently exposed only as NOT_IMPLEMENTED.',
        'URL': '/synthetic_tabular_data_generator/synthesizer_config/llm_nearest_neighbor_knowledge_grounded_text_synthesis.yaml'
    },
    'llm_text_only_paraphrase_synthesis': {
        'version': '0.1',
        'type': 'cross-sectional',
        'class': LlmTextOnlyParaphraseSynthesisSynthesizer,
        'display_name': 'LLM Text-only Paraphrase Synthesis',
        'description': 'Rewrite a single TEXT input column with an LLM while preserving the original information content. This synthesizer expects exactly one free-text column and rephrases each non-missing value without changing meaning.',
        'URL': '/synthetic_tabular_data_generator/synthesizer_config/llm_text_only_paraphrase_synthesis.yaml'
    },
    'llm_text_only_indirect_identifier_rewrite_synthesis': {
        'version': '0.1',
        'type': 'cross-sectional',
        'class': LlmTextOnlyIndirectIdentifierRewriteSynthesisSynthesizer,
        'display_name': 'LLM Text-only Indirect Identifier Rewrite Synthesis',
        'description': 'Rewrite a single TEXT input column with an LLM while preserving medically relevant content and generalizing direct or indirect identifiers. This synthesizer expects exactly one free-text column and produces exactly one rewritten output row per input row.',
        'URL': '/synthetic_tabular_data_generator/synthesizer_config/llm_text_only_indirect_identifier_rewrite_synthesis.yaml'
    },
    'llm_text_only_semantic_variation_synthesis': {
        'version': '0.1',
        'type': 'cross-sectional',
        'class': LlmTextOnlySemanticVariationSynthesisSynthesizer,
        'display_name': 'LLM Text-only Semantic Variation Synthesis',
        'description': 'Generate a new single TEXT column for fictional but plausible patients by sampling source texts and asking an LLM to create semantically similar variations. This synthesizer expects exactly one free-text column.',
        'URL': '/synthetic_tabular_data_generator/synthesizer_config/llm_text_only_semantic_variation_synthesis.yaml'
    }
}

# Keep hyperparameter-tuning metadata separate so the public synthesizer registry
# remains compatible with `main`.
synthesizer_tuning_metadata = {
    'ctgan': {'supported': True, 'direction': 'minimize'},
    'tvae': {'supported': True, 'direction': 'minimize'},
    'bayesian_network': {'supported': True, 'direction': 'maximize'},
    'arf': {'supported': True, 'direction': 'minimize'},
    'ddpm': {'supported': True, 'direction': 'minimize'},
    'rtvae': {'supported': True, 'direction': 'minimize'},
}
