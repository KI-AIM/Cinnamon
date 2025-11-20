import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import OrdinalEncoder, LabelEncoder, MinMaxScaler
from utility.lazypredict.Supervised import LazyClassifier, LazyRegressor, REGRESSORS, CLASSIFIERS
from sklearn.utils import shuffle

VALID_REGRESSORS = [
    "LinearRegression",
    "SVR",
    "KNeighborsRegressor",
    "DecisionTreeRegressor",
    "RandomForestRegressor",
    "MLPRegressor"
]

VALID_CLASSIFIERS = [
    "LogisticRegression",
    "SVC",
    "KNeighborsClassifier",
    "DecisionTreeClassifier",
    "RandomForestClassifier", 
    "MLPClassifier"
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
    3: {'min': 0.1, 'max': 0.2},  # Very Small
    5: {'min': 0.2, 'max': 0.3},  # Small
    6: {'min': 0.3, 'max': 0.4},  # Moderate
    7: {'min': 0.4, 'max': 0.5},  # Notable
    8: {'min': 0.5, 'max': 0.6},  # Significant
    9: {'min': 0.6, 'max': 0.7},  # Large
    10: {'min': 0.7, 'max': 1.0},  # Very Large
}

DISCRIMINATOR_RANGES = {
    1: {'min': 0.00, 'max': 0.05},  # Ideal (≤ ±0.05 away from random guess)
    2: {'min': 0.05, 'max': 0.10},  # Excellent
    3: {'min': 0.10, 'max': 0.15},  # Very Good
    4: {'min': 0.15, 'max': 0.20},  # Good
    5: {'min': 0.20, 'max': 0.25},  # Above Average
    6: {'min': 0.25, 'max': 0.30},  # Average
    7: {'min': 0.30, 'max': 0.35},  # Below Average
    8: {'min': 0.35, 'max': 0.40},  # Poor
    9: {'min': 0.40, 'max': 0.45},  # Very Poor
    10: {'min': 0.45, 'max': float('inf')}  # Insufficient
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


def preprocess_features(train_df: pd.DataFrame, test_df: pd.DataFrame):
    """
    Fit scaler/encoder on the training split only and apply to both train and test.
    Unknown categories map to -1 to stay robust when new codes appear.
    """
    train_processed = train_df.copy()
    test_processed = test_df.copy()

    numeric_cols = train_processed.select_dtypes(include=['number']).columns
    if len(numeric_cols) > 0:
        scaler = MinMaxScaler()
        train_processed[numeric_cols] = scaler.fit_transform(train_processed[numeric_cols])
        test_processed[numeric_cols] = scaler.transform(test_processed[numeric_cols])

    categorical_cols = train_processed.select_dtypes(exclude=['number']).columns
    if len(categorical_cols) > 0:
        encoder = OrdinalEncoder(handle_unknown='use_encoded_value', unknown_value=-1)
        train_processed[categorical_cols] = encoder.fit_transform(train_processed[categorical_cols])
        test_processed[categorical_cols] = encoder.transform(test_processed[categorical_cols])

    return train_processed, test_processed


def calculate_machine_learning_utility(real: pd.DataFrame, synthetic: pd.DataFrame, train_size: float, target_variable: str, random_state: int = 42):
    """
    Calculates the machine learning utility of a synthetic dataset compared to a real dataset.
    Uses synthetic data for training and real data for testing.

    Args:
        real (pandas.DataFrame): The real dataset.
        synthetic (pandas.DataFrame): The synthetic dataset.
        train_size (float): The proportion of the real dataset used for training (test_size = 1 - train_size).
        target_variable (str): The target variable (column) in the dataset to predict.
        random_state (int, optional): Seed for reproducibility. Defaults to 42.

    Returns:
        dict: A dictionary containing the machine learning utility of the synthetic dataset compared to the real dataset.
    """
    machine_learning_dict = {'real': {'predictions': {}}, 'synthetic': {'predictions': {}}, 'difference': {}}

    test_size = 1 - train_size

    # Handle missing values with DELETE as the only marker
    real = impute_missing_values(real, 'DELETE')
    synthetic = impute_missing_values(synthetic, 'DELETE')

    # Separate features and targets
    X_real_full = real.drop(columns=[target_variable])
    y_real_full = real[target_variable]
    X_synth_full = synthetic.drop(columns=[target_variable])
    y_synth_full = synthetic[target_variable]

    # Split real data for baseline and holdout
    X_train_real_raw, X_test_real_raw, y_train_real, y_test_real = train_test_split(
        X_real_full, y_real_full, test_size=test_size, random_state=random_state
    )

    # Split synthetic data to mirror the real split (prevents leakage when datasets align)
    X_train_synth_raw, _, y_train_synth, _ = train_test_split(
        X_synth_full, y_synth_full, test_size=test_size, random_state=random_state
    )

    # Preprocess for the synthetic-trained model: fit on synthetic train, apply to real holdout
    X_train_synth, X_test_synth = preprocess_features(X_train_synth_raw, X_test_real_raw)

    # Preprocess for the real-trained baseline: fit on real train, apply to real test
    X_train_real, X_test_real = preprocess_features(X_train_real_raw, X_test_real_raw)

    # Set up train (synthetic) and real data (already split)
    X_train = X_train_synth
    y_train = y_train_synth
    X_test = X_test_synth
    # y_test stays the real holdout labels
    y_test = y_test_real
    
    if pd.api.types.is_numeric_dtype(y_test):
        print('Regression Activated')
        
        # Scale target variables CONSISTENTLY using the same scaler (no test leakage)
        y_min = min(y_train.min(), y_train_real.min())
        y_max = max(y_train.max(), y_train_real.max())
        
        if y_max > y_min:
            y_range = y_max - y_min
            y_train_scaled = y_train.apply(lambda x: (x - y_min) / y_range)
            y_test_scaled = y_test.apply(lambda x: (x - y_min) / y_range)
            y_train_real_scaled = y_train_real.apply(lambda x: (x - y_min) / y_range)
            y_test_real_scaled = y_test_real.apply(lambda x: (x - y_min) / y_range)
        else:
            # If no range (constant target), don't scale
            y_train_scaled = y_train
            y_test_scaled = y_test
            y_train_real_scaled = y_train_real
            y_test_real_scaled = y_test_real
        
        # Get filtered regressors
        filtered_regressors = [reg for reg in REGRESSORS if reg[0] in VALID_REGRESSORS]
        
        # Train on synthetic, test on real
        lazy = LazyRegressor(verbose=0, ignore_warnings=True, custom_metric=None, regressors=filtered_regressors)
        models_synthetic, predictions_synthetic = lazy.fit(
            X_train, X_test, y_train_scaled, y_test_scaled
        )
        
        # Train and test on real (for comparison)
        models_real, predictions_real = lazy.fit(
            X_train_real, X_test_real, y_train_real_scaled, y_test_real_scaled
        )

        # Convert to dicts for consistent downstream handling
        predictions_real = predictions_real.to_dict()
        predictions_synthetic = predictions_synthetic.to_dict()

        # Normalise negative R-squared values to zero so dashboards stay in the configured [0,1] range
        for col in ('R-Squared', 'Adjusted R-Squared'):
            if col in predictions_real:
                predictions_real[col] = {k: max(0.0, v) for k, v in predictions_real[col].items()}
            if col in predictions_synthetic:
                predictions_synthetic[col] = {k: max(0.0, v) for k, v in predictions_synthetic[col].items()}

        predictions_real = add_summary_classifier(predictions_real)
        predictions_synthetic = add_summary_classifier(predictions_synthetic)
        machine_learning_dict['real']['predictions'] = predictions_real
        machine_learning_dict['synthetic']['predictions'] = predictions_synthetic
        machine_learning_dict['difference'] = calculate_differences_as_dict(machine_learning_dict)
    else:
        print('Classification Activated')
        # Encode labels without leaking test labels: fit separately on each training set
        le_synth = LabelEncoder()
        le_synth.fit(y_train)
        y_train_encoded = le_synth.transform(y_train)
        y_test_encoded = le_synth.transform(y_test)
        
        le_real = LabelEncoder()
        le_real.fit(y_train_real)
        y_train_real_encoded = le_real.transform(y_train_real)
        y_test_real_encoded = le_real.transform(y_test_real)
        
        # Get filtered classifiers
        filtered_classifiers = [clf for clf in CLASSIFIERS if clf[0] in VALID_CLASSIFIERS]
        
        # Train on synthetic, test on real
        lazy = LazyClassifier(verbose=0, ignore_warnings=True, custom_metric=None, classifiers=filtered_classifiers)
        models_synthetic, predictions_synthetic = lazy.fit(
            X_train, X_test, y_train_encoded, y_test_encoded
        )
        
        # Train and test on real (for comparison)
        models_real, predictions_real = lazy.fit(
            X_train_real, X_test_real, y_train_real_encoded, y_test_real_encoded
        )
        
        predictions_real = remove_roc_auc(predictions_real.to_dict())
        predictions_synthetic = remove_roc_auc(predictions_synthetic.to_dict())
        predictions_real = add_summary_classifier(predictions_real)
        predictions_synthetic = add_summary_classifier(predictions_synthetic)
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


def discriminator_based_evaluation(real: pd.DataFrame, synthetic: pd.DataFrame, train_size: float, random_state: int = 42) -> dict:
    """
    Evaluate synthetic data quality using a discriminator-based approach with balanced datasets.

    Args:
        real (pd.DataFrame): Real dataset
        synthetic (pd.DataFrame): Synthetic dataset
        train_size (float): Proportion of data to use for training (test_size = 1 - train_size)
        random_state (int, optional): Seed for reproducibility. Defaults to 42.

    Returns:
        dict: Dictionary containing evaluation metrics with color coding
    """
    test_size = 1 - train_size
    cols_to_drop = []

    # Identify columns with 100% NA in the real dataset
    na_cols = real.columns[real.isna().all()].tolist()
    cols_to_drop.extend(na_cols)

    # Identify categorical columns with 100% unique values in the real dataset
    categorical_cols_real = real.select_dtypes(include=['object', 'category']).columns
    for col in categorical_cols_real:
        if col not in cols_to_drop:
            if real[col].nunique(dropna=False) == len(real):
                cols_to_drop.append(col)

    # Identify columns with 100% NA in the synthetic dataset
    na_cols_synthetic = synthetic.columns[synthetic.isna().all()].tolist()
    for col_synth in na_cols_synthetic:
         cols_to_drop.append(col_synth)

    # Identify categorical columns with all unique values in synthetic
    categorical_cols_synthetic = synthetic.select_dtypes(include=['object', 'category']).columns
    for col_synth in categorical_cols_synthetic:
        if col_synth not in cols_to_drop:
            if synthetic[col_synth].nunique(dropna=False) == len(synthetic):
                cols_to_drop.append(col_synth)

    # Drop constant columns also in real (symmetry)
    for col_real in real.columns:
        if col_real not in cols_to_drop:
            if real[col_real].nunique(dropna=False) == 1:
                cols_to_drop.append(col_real)

    # Drop constant columns in synthetic
    for col_synth in synthetic.columns:
        if col_synth not in cols_to_drop:
            if synthetic[col_synth].nunique(dropna=False) == 1:
                cols_to_drop.append(col_synth)

    # Ensure unique columns to drop
    cols_to_drop = list(set(cols_to_drop))

    if cols_to_drop:
        print(f"Dropping columns from real and synthetic datasets: {cols_to_drop}")
        real = real.drop(columns=[c for c in cols_to_drop if c in real.columns])
        synthetic = synthetic.drop(columns=[c for c in cols_to_drop if c in synthetic.columns])

    # Balance datasets by sampling from the larger one
    min_size = min(len(real), len(synthetic))
    if len(real) > min_size:
        real = real.sample(n=min_size, random_state=random_state)
    elif len(synthetic) > min_size:
        synthetic = synthetic.sample(n=min_size, random_state=random_state)

    # Add synthetic indicator column
    real['Synthetic'] = 0
    synthetic['Synthetic'] = 1

    real = impute_missing_values(real, 'DELETE')
    synthetic = impute_missing_values(synthetic, 'DELETE')

    # Merge and shuffle datasets
    merged_data = pd.concat([real, synthetic], ignore_index=True)
    merged_data = shuffle(merged_data, random_state=random_state)
    merged_data = merged_data.dropna()

    # Split features and target
    y = merged_data["Synthetic"]
    merged_data = merged_data.drop(columns=["Synthetic"])

    # Split data
    X = merged_data
    X_train_raw, X_test_raw, y_train, y_test = train_test_split(
        X, y, test_size=test_size, random_state=random_state
    )

    # Fit encoder/scaler on train only
    X_train, X_test = preprocess_features(X_train_raw, X_test_raw)

    # Train and evaluate classifiers
    filtered_classifiers = [clf for clf in CLASSIFIERS if clf[0] in VALID_CLASSIFIERS]
    lazy = LazyClassifier(verbose=0, ignore_warnings=False, custom_metric=None, classifiers=filtered_classifiers)
    models, predictions = lazy.fit(X_train, X_test, y_train, y_test)
    predictions = remove_roc_auc(predictions.to_dict())
    predictions = add_summary_classifier(predictions)

    return transform_predictions_with_color_coding(predictions, DISCRIMINATOR_RANGES, pivot=0.5)


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
    Determine color index (1-10) based on provided value and interpretation ranges.

    Args:
        value (float): Value to be evaluated
        interpretation_ranges (dict): Dictionary defining the ranges and their corresponding indices

    Returns:
        int: Color index between 1 and 10 (1=best, 10=worst)
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


def transform_predictions_with_color_coding(predictions, interpretation_ranges, pivot=None):
    """
    Transform predictions dictionary to include color coding for all metrics.
    Time Taken metric will always have color_index=0.

    Args:
        predictions (dict): Dictionary containing metric scores for different classifiers
        interpretation_ranges (dict): Dictionary defining the ranges for color coding
        pivot (float, optional): Central value for symmetric evaluation (e.g. 0.5 for discriminator scores)

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
                color_value = abs(score - pivot) if (pivot is not None and score is not None) else score
                result = {
                    'classifier': classifier,
                    'score': score,
                    'color_index': get_color_index(color_value, interpretation_ranges)
                }
            results_dict['predictions'][metric].append(result)

    return results_dict

def remove_roc_auc(predictions_dict):
    if 'ROC AUC' in predictions_dict:
        predictions_dict.pop('ROC AUC')
    return predictions_dict



def impute_missing_values(df: pd.DataFrame, missing_value_placeholder: str = "DELETE") -> pd.DataFrame:
    """
    Imputes missing values in a DataFrame.
    - Numeric columns are imputed with the mean of the column.
    - Categorical columns are imputed with a placeholder value and cast to string.

    Args:
        df (pd.DataFrame): The DataFrame to impute.
        missing_value_placeholder (str, optional): The placeholder for missing categorical values.
            Defaults to "DELETE".

    Returns:
        pd.DataFrame: The imputed DataFrame.
    """
    df_imputed = df.copy()

    categorical_cols = df_imputed.select_dtypes(exclude=['number']).columns
    placeholder_normalized = missing_value_placeholder.strip().upper()
    if len(categorical_cols) > 0:
        for col in categorical_cols:
            col_series = df_imputed[col]
            placeholder_mask = col_series.astype(str).str.strip().str.upper() == placeholder_normalized
            df_imputed.loc[placeholder_mask, col] = pd.NA
    
    numeric_cols = df_imputed.select_dtypes(include=['number']).columns
    for col in numeric_cols:
        mean_val = df_imputed[col].mean()
        impute_val = 0 if pd.isna(mean_val) else mean_val
        df_imputed[col] = df_imputed[col].fillna(impute_val)

    for col in categorical_cols:
        df_imputed[col] = df_imputed[col].fillna(missing_value_placeholder).astype(str)
    
    return df_imputed

def add_summary_classifier(predictions: dict) -> dict:
    """
    Add a Summary classifier to aggregate scores from all classifiers for each metric.
    
    Args:
        predictions (dict): Dictionary containing metrics with classifier scores
        
    Returns:
        dict: Updated dictionary with Summary classifier added to each metric
    """
    updated_dict = {}
    
    for metric, classifiers in predictions.items():
        # Create a new dictionary for this metric
        updated_dict[metric] = classifiers.copy()
        
        # Calculate average score for all classifiers except Summary (in case it already exists)
        classifier_scores = [score for classifier, score in classifiers.items() 
                            if classifier != 'Summary']
        
        if classifier_scores:
            summary_score = sum(classifier_scores) / len(classifier_scores)
            updated_dict[metric]['Summary'] = summary_score
    
    return updated_dict
