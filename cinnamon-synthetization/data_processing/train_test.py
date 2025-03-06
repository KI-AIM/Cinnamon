

def split_train_test_cross_sectional(fitting_config, dataset, seed=42):
    """
    Split a cross-sectional dataset into training and validation sets.

    This function shuffles the dataset to ensure randomization and splits it into
    training and validation sets based on the proportions specified in the configuration.
    It guarantees reproducibility by using a fixed random seed.

    Args:
        fitting_config (dict): Configuration dictionary specifying the split ratio.
            Must include the key 'train' with a value between 0 and 1, representing
            the proportion of the dataset to be used for training.
        dataset (pd.DataFrame): The input dataset in tabular format.
        seed (int, optional): Random seed for reproducibility. Default is 42.

    Returns:
        tuple:
            - pd.DataFrame: The training dataset.
            - pd.DataFrame: The validation dataset.
    """

    shuffled_dataset = dataset.sample(frac=1, replace=False, random_state=seed)
    train_size = int(fitting_config['train'] * len(shuffled_dataset))
    train_dataset = shuffled_dataset[:train_size]
    validate_dataset = shuffled_dataset[train_size:]

    return train_dataset, validate_dataset
