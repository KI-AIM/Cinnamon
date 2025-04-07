import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import OrdinalEncoder, minmax_scale, LabelEncoder
from utility.lazypredict.Supervised import LazyClassifier, LazyRegressor, REGRESSORS, CLASSIFIERS
from sklearn.utils import shuffle

VALID_REGRESSORS = [
    "LinearRegression",
    "SVR",
    "KNeighborsRegressor",
    "DecisionTreeRegressor",
    "RandomForestRegressor",
]

VALID_CLASSIFIERS = [
    "LogisticRegression",
    "SVC",
    "KNeighborsClassifier",
    "DecisionTreeClassifier",
    "RandomForestClassifier",
]

MACHINE_LEARNING_RANGES = {
    1: {'min': 0.9, 'max': 1.0},  # Perfect
    2: {'min': 0.8, 'max': 0.9},  # Excellent
    3: {'min': 0.7, 'max': 0.8},  # Very Good
    4: {'min': 0.6, 'max': 0.7},  # Good
    5: {'min': 0.5, 'max': 0.6},  # Above Average
    6: {'min': 0.4, 'max': 0.5},  # Average
    7: {'min': 0.3, 'max': 0.4},  # Below Average
    8: {'min': 0.2, 'max': 0.3},  # Poor
    9: {'min': 0.1, 'max': 0.2},  # Very Poor
    10: {'min': 0.0, 'max': 0.1}  # Insufficient
}

MACHINE_LEARNING_DIFFERENCES = {
    1: {'min': 0.0, 'max': 0.1},  # Minimal
    2: {'min': 0.1, 'max': 0.2},  # Very Small
    3: {'min': 0.2, 'max': 0.3},  # Small
    4: {'min': 0.3, 'max': 0.4},  # Moderate
    5: {'min': 0.4, 'max': 0.5},  # Notable
    6: {'min': 0.5, 'max': 0.6},  # Significant
    7: {'min': 0.6, 'max': 0.7},  # Large
    8: {'min': 0.7, 'max': 0.8},  # Very Large
    9: {'min': 0.8, 'max': 0.9},  # Extreme
    10: {'min': 0.9, 'max': 1.0}  # Maximum
}

DISCRIMINATOR_RANGES = {
    1: {'min': 0.45, 'max': 0.50},  # Ideal
    2: {'min': 0.50, 'max': 0.55},  # Excellent
    3: {'min': 0.55, 'max': 0.60},  # Very Good
    4: {'min': 0.60, 'max': 0.65},  # Good
    5: {'min': 0.65, 'max': 0.70},  # Above Average
    6: {'min': 0.70, 'max': 0.75},  # Average
    7: {'min': 0.75, 'max': 0.80},  # Below Average
    8: {'min': 0.80, 'max': 0.85},  # Poor
    9: {'min': 0.85, 'max': 0.90},  # Very Poor
    10: {'min': 0.90, 'max': 1.00}  # Insufficient
}

RMSE_RANGES = {
    1: {'min': 0.0, 'max': 0.1},  # Excellent
    2: {'min': 0.1, 'max': 0.15},  # Very Good
    3: {'min': 0.15, 'max': 0.20},  # Good
    4: {'min': 0.20, 'max': 0.25},  # Fair
    5: {'min': 0.25, 'max': 0.30},  # Moderate
    6: {'min': 0.30, 'max': 0.35},  # Below Average
    7: {'min': 0.35, 'max': 0.40},  # Poor
    8: {'min': 0.40, 'max': 0.45},  # Very Poor
    9: {'min': 0.45, 'max': 0.50},  # Bad
    10: {'min': 0.50, 'max': float('inf')}  # Very Bad
}


def calculate_machine_learning_utility(real: pd.DataFrame, synthetic: pd.DataFrame, train_size, random_state,
                                       target_variable: str):
    """
    Calculates the machine learning utility of a synthetic dataset compared to a real dataset.

    Args:
        real (pandas.DataFrame): The real dataset.
        synthetic (pandas.DataFrame): The synthetic dataset.
        train_size (float): The proportion of the dataset to include in the train split.
        random_state (int): The random seed used by the random number generator to ensure reproducibility.
        target_variable (str): The target variable (column) in the dataset to predict.

    Returns:
        dict: A dictionary containing the machine learning utility of the synthetic dataset compared to the real dataset.
    """
    machine_learning_dict = {'real': {'predictions': {}}, 'synthetic': {'predictions': {}}, 'difference': {}}
    test_size = 1 - train_size

    real_target = real[target_variable]
    synthetic_target = synthetic[target_variable]

    real = impute_missing_values(real, 'MISSING_VALUE')
    synthetic = impute_missing_values(synthetic, 'MISSING_VALUE')

    real_features = real.drop(columns=[target_variable])
    synthetic_features = synthetic.drop(columns=[target_variable])
    combined_features = pd.concat([real_features, synthetic_features], axis=0)

    # Apply MinMax scaling to numeric columns
    numeric_cols = combined_features.select_dtypes(include=['number']).columns
    combined_features[numeric_cols] = minmax_scale(combined_features[numeric_cols])

    # Apply Ordinal Encoding to categorical columns
    categorical_cols = combined_features.select_dtypes(exclude=['number']).columns
    encoder = OrdinalEncoder()
    combined_features[categorical_cols] = encoder.fit_transform(combined_features[categorical_cols])

    # Split back into real and synthetic datasets
    real_processed = combined_features.iloc[:len(real_features)].copy()
    synthetic_processed = combined_features.iloc[len(real_features):].copy()

    # Add the target back to the datasets
    real_processed[target_variable] = real_target.values
    synthetic_processed[target_variable] = synthetic_target.values

    # Drop all NAs
    real_processed = real_processed.dropna()
    synthetic_processed = synthetic_processed.dropna()

    # Split real data into train and test sets
    X_real = real_processed.drop(columns=[target_variable])
    y_real = real_processed[target_variable]

    X_train_real, X_test_real, y_train_real, y_test_real = train_test_split(
        X_real, y_real, test_size=test_size, train_size=train_size, random_state=random_state
    )

    # Split synthetic data into train and test sets (using same method for consistency)
    X_synthetic = synthetic_processed.drop(columns=[target_variable])
    y_synthetic = synthetic_processed[target_variable]

    X_train_synthetic, X_test_synthetic, y_train_synthetic, y_test_synthetic = train_test_split(
        X_synthetic, y_synthetic, test_size=test_size, train_size=train_size, random_state=random_state
    )

    if pd.api.types.is_numeric_dtype(y_real):
        print('Regression Activated')
        # Scale y between 0 and 1
        y_train_real = y_train_real.apply(lambda x: (x - min(y_train_real)) / (max(y_train_real) - min(y_train_real)))
        y_test_real = y_test_real.apply(lambda x: (x - min(y_test_real)) / (max(y_test_real) - min(y_test_real)))
        y_train_synthetic = y_train_synthetic.apply(lambda x: (x - min(y_train_synthetic)) / (max(y_train_synthetic)
                                                                                              - min(y_train_synthetic)))
        y_test_synthetic = y_test_synthetic.apply(lambda x: (x - min(y_test_synthetic)) / (max(y_test_synthetic)
                                                                                           - min(y_test_synthetic)))

        filtered_regressors = [reg for reg in REGRESSORS if reg[0] in VALID_REGRESSORS]
        lazy = LazyRegressor(verbose=0, ignore_warnings=False, custom_metric=None, regressors=filtered_regressors)
        models_synthetic, predictions_synthetic = lazy.fit(X_train_synthetic, X_test_real, y_train_synthetic,
                                                           y_test_real)

        models_real, predictions_real = lazy.fit(X_train_real, X_test_real, y_train_real, y_test_real)
        machine_learning_dict['real']['predictions'] = predictions_real.to_dict()
        machine_learning_dict['synthetic']['predictions'] = predictions_synthetic.to_dict()
        machine_learning_dict['difference'] = calculate_differences_as_dict(machine_learning_dict)
    else:
        print('Classification Activated')
        # Encode Lables with ordinal encoding since Lazypredict handels it internally if ordinal or onehot is
        # #choosen in case of classification
        le = LabelEncoder()
        y_train_synthetic = le.fit_transform(y_train_synthetic)
        y_test_real = le.transform(y_test_real)
        y_train_real = le.fit_transform(y_train_real)

        filtered_classifiers = [clf for clf in CLASSIFIERS if clf[0] in VALID_CLASSIFIERS]
        lazy = LazyClassifier(verbose=0, ignore_warnings=False, custom_metric=None, classifiers=filtered_classifiers)
        models_synthetic, predictions_synthetic = lazy.fit(X_train_synthetic, X_test_real, y_train_synthetic,
                                                           y_test_real)

        models_real, predictions_real = lazy.fit(X_train_real, X_test_real, y_train_real, y_test_real)
        predictions_real = remove_roc_auc(predictions_real.to_dict())
        predictions_synthetic = remove_roc_auc(predictions_synthetic.to_dict())
        machine_learning_dict['real']['predictions'] = predictions_real
        machine_learning_dict['synthetic']['predictions'] = predictions_synthetic
        machine_learning_dict['difference'] = calculate_differences_as_dict(machine_learning_dict)

    machine_learning_dict['real'] = transform_predictions_with_color_coding(
        predictions_real, MACHINE_LEARNING_RANGES)

    machine_learning_dict['synthetic'] = transform_predictions_with_color_coding(
        predictions_synthetic, MACHINE_LEARNING_RANGES)

    machine_learning_dict['difference'] = transform_predictions_with_color_coding(
        machine_learning_dict['difference']['predictions'], MACHINE_LEARNING_DIFFERENCES)

    return machine_learning_dict


def discriminator_based_evaluation(real: pd.DataFrame, synthetic: pd.DataFrame, train_size, random_state):
    """
    Evaluate synthetic data quality using discriminator-based approach.

    Args:
        real (pd.DataFrame): Real dataset
        synthetic (pd.DataFrame): Synthetic dataset
        train_size (float): Proportion of data to use for training
        random_state (int): Random seed for reproducibility

    Returns:
        dict: Dictionary containing evaluation metrics with color coding
    """
    test_size = 1 - train_size

    # Add synthetic indicator column
    real['Synthetic'] = 0
    synthetic['Synthetic'] = 1

    real = impute_missing_values(real, 'MISSING_VALUE')
    synthetic = impute_missing_values(synthetic, 'MISSING_VALUE')

    # Merge and shuffle datasets
    merged_data = pd.concat([real, synthetic], ignore_index=True)
    merged_data = shuffle(merged_data, random_state=random_state)
    merged_data = merged_data.dropna()

    # Split features and target
    y = merged_data["Synthetic"]
    merged_data = merged_data.drop(columns=["Synthetic"])

    # Scale numeric features
    numeric_cols = merged_data.select_dtypes(include=['number']).columns
    merged_data[numeric_cols] = minmax_scale(merged_data[numeric_cols])

    # Encode categorical features
    categorical_cols = merged_data.select_dtypes(exclude=['number']).columns
    merged_data[categorical_cols] = merged_data[categorical_cols].astype(str)
    encoder = OrdinalEncoder()
    merged_data[categorical_cols] = encoder.fit_transform(merged_data[categorical_cols])

    # Split data
    X = merged_data
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=test_size, train_size=train_size, random_state=random_state
    )

    # Train and evaluate classifiers
    filtered_classifiers = [clf for clf in CLASSIFIERS if clf[0] in VALID_CLASSIFIERS]
    lazy = LazyClassifier(verbose=0, ignore_warnings=False, custom_metric=None, classifiers=filtered_classifiers)
    models, predictions = lazy.fit(X_train, X_test, y_train, y_test)
    predictions = remove_roc_auc(predictions.to_dict())

    return transform_predictions_with_color_coding(predictions, DISCRIMINATOR_RANGES)


def calculate_differences_as_dict(result_dict):
    """
    Calculates the differences between the predictions of a real and synthetic dataset.

    Args:
        result_dict (dict): A dictionary containing the predictions of the real and synthetic datasets.

    Returns:
        dict: A dictionary containing the differences between the predictions of the real and synthetic datasets.
    """
    differences = {"predictions": {}}
    real = result_dict["real"]
    synthetic = result_dict["synthetic"]

    for key in ["predictions"]:
        if key not in real or key not in synthetic:
            continue
            
        for metric, classifiers in real[key].items():
            if metric == "ROC AUC":
                continue
                
            if metric not in differences[key]:
                differences[key][metric] = {}
                
            for clf, real_value in classifiers.items():
                synthetic_value = None
                if metric in synthetic[key] and clf in synthetic[key][metric]:
                    synthetic_value = synthetic[key][metric][clf]
                
                if real_value is not None and synthetic_value is not None:
                    differences[key][metric][clf] = abs(real_value - synthetic_value)
                else:
                    differences[key][metric][clf] = None

    return differences


def get_color_index(value, interpretation_ranges):
    """
    Determine color index (1-5) based on provided value and interpretation ranges.

    Args:
        value (float): Value to be evaluated
        interpretation_ranges (dict): Dictionary defining the ranges and their corresponding indices

    Returns:
        int: Color index between 1 and 5 (1=best, 5=worst)
    """
    # Check for negative values first
    if value < 0:
        return 10

    for index, range_values in interpretation_ranges.items():
        if range_values['min'] is None or range_values['max'] is None:
            continue

        if range_values['min'] <= value <= range_values['max']:
            return index

    return 10


def get_color_index_rmse(value, interpretation_ranges):
    """
    Determine color index (1-10) based on provided value and interpretation ranges.
    Lower values get better scores (closer to 1).
    Negative values automatically get assigned 10.

    Args:
        value (float): Value to be evaluated (e.g., RMSE)
        interpretation_ranges (dict): Dictionary defining the ranges and their corresponding indices

    Returns:
        int: Color index between 1 and 10 (1=best/lowest, 10=worst/highest)
    """
    # Check for negative values first
    if value < 0:
        return 10

    for index, range_values in interpretation_ranges.items():
        if range_values['min'] is None or range_values['max'] is None:
            continue

        if range_values['min'] <= value < range_values['max']:
            return index

    return 10


def transform_predictions_with_color_coding(predictions, interpretation_ranges):
    """
    Transform predictions dictionary to include color coding for all metrics.
    Time Taken metric will always have color_index=0.

    Args:
        predictions (dict): Dictionary containing metric scores for different classifiers
        interpretation_ranges (dict): Dictionary defining the ranges for color coding

    Returns:
        dict: Transformed dictionary with color coding for all metrics
    """
    results_dict = {'predictions': {}}

    for metric, values in predictions.items():
        results_dict['predictions'][metric] = []
        for classifier, score in values.items():
            if metric == "Time Taken":
                result = {
                    'classifier': classifier,
                    'score': score,
                    'color_index': 0
                }
            elif metric == "RMSE":
                result = {
                    'classifier': classifier,
                    'score': score,
                    'color_index': get_color_index_rmse(score, RMSE_RANGES)
                }
            else:
                result = {
                    'classifier': classifier,
                    'score': score,
                    'color_index': get_color_index(score, interpretation_ranges)
                }
            results_dict['predictions'][metric].append(result)

    return results_dict

def remove_roc_auc(predictions_dict):
    if 'ROC AUC' in predictions_dict:
        predictions_dict.pop('ROC AUC')
    return predictions_dict



def impute_missing_values(df: pd.DataFrame, missing_value_placeholder: str = "MISSING_VALUE") -> pd.DataFrame:
    """
    Imputes missing values in a DataFrame.
    - Numeric columns are imputed with the mean of the column.
    - Categorical columns are converted to strings and imputed with a placeholder value.

    Args:
        df (pd.DataFrame): The DataFrame to impute.
        missing_value_placeholder (str, optional): The placeholder for missing categorical values.
            Defaults to "MISSING_VALUE".

    Returns:
        pd.DataFrame: The imputed DataFrame.
    """
    df_imputed = df.copy()
    
    numeric_cols = df_imputed.select_dtypes(include=['number']).columns
    for col in numeric_cols:
        mean_val = df_imputed[col].mean()
        df_imputed[col] = df_imputed[col].fillna(mean_val)
    
    categorical_cols = df_imputed.select_dtypes(exclude=['number']).columns
    for col in categorical_cols:
        df_imputed[col] = df_imputed[col].astype(str)  
        df_imputed[col] = df_imputed[col].fillna(missing_value_placeholder)
    
    return df_imputed
