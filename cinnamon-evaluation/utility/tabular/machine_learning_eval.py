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


def calculate_machine_learning_utility(real: pd.DataFrame, synthetic: pd.DataFrame, train_size, target_variable: str):
    """
    Calculates the machine learning utility of a synthetic dataset compared to a real dataset.
    Uses synthetic data for training and real data for testing.

    Args:
        real (pandas.DataFrame): The real dataset.
        synthetic (pandas.DataFrame): The synthetic dataset.
        train_size (float): The proportion of the dataset to include in the train split (not used in this version).
        random_state (int): The random seed used by the random number generator to ensure reproducibility.
        target_variable (str): The target variable (column) in the dataset to predict.

    Returns:
        dict: A dictionary containing the machine learning utility of the synthetic dataset compared to the real dataset.
    """
    machine_learning_dict = {'real': {'predictions': {}}, 'synthetic': {'predictions': {}}, 'difference': {}}

    test_size = 1- train_size
    random_state = 42

    # Store original target values before any transformation
    real_target_original = real[target_variable].copy()
    synthetic_target_original = synthetic[target_variable].copy()

    # Handle missing values
    real = impute_missing_values(real, 'MISSING_VALUE')
    synthetic = impute_missing_values(synthetic, 'MISSING_VALUE')

    # Separate features and targets
    real_features = real.drop(columns=[target_variable])
    synthetic_features = synthetic.drop(columns=[target_variable])
    
    # Combine feature sets for consistent preprocessing
    combined_features = pd.concat([real_features, synthetic_features], axis=0)
    
    # Apply preprocessing to combined features
    numeric_cols = combined_features.select_dtypes(include=['number']).columns
    if len(numeric_cols) > 0:
        combined_features[numeric_cols] = minmax_scale(combined_features[numeric_cols])
    
    categorical_cols = combined_features.select_dtypes(exclude=['number']).columns
    if len(categorical_cols) > 0:
        encoder = OrdinalEncoder(handle_unknown='use_encoded_value', unknown_value=-1)
        combined_features[categorical_cols] = encoder.fit_transform(combined_features[categorical_cols])
    
    # Split back into separate datasets
    real_processed = combined_features.iloc[:len(real_features)].copy()
    synthetic_processed = combined_features.iloc[len(real_features):].copy()
    
    # Add the target back (use original values)
    real_processed[target_variable] = real_target_original.values
    synthetic_processed[target_variable] = synthetic_target_original.values
    
    # Remove any rows with NaN values
    real_processed = real_processed.dropna()
    synthetic_processed = synthetic_processed.dropna()
    
    # Set up train (synthetic) and test (real) data
    X_train = synthetic_processed.drop(columns=[target_variable])
    y_train = synthetic_processed[target_variable]
    
    X_test = real_processed.drop(columns=[target_variable])
    y_test = real_processed[target_variable]
    
    # For the real model (train and test on real data)
    # Create a split of real data for comparison
    X_train_real, X_test_real, y_train_real, y_test_real = train_test_split(
        X_test, y_test, test_size=test_size, random_state=random_state
    )
    
    if pd.api.types.is_numeric_dtype(y_test):
        print('Regression Activated')
        
        # Scale target variables CONSISTENTLY using the same scaler
        # This avoids distortion when comparing real vs synthetic models
        y_min = min(y_train.min(), y_test.min())
        y_max = max(y_train.max(), y_test.max())
        
        # Avoid division by zero
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

        # Apply the R-squared floor for both real and synthetic models
        predictions_real['R-Squared'] = predictions_real['R-Squared'].apply(lambda x: max(0.0, x))
        predictions_synthetic['R-Squared'] = predictions_synthetic['R-Squared'].apply(lambda x: max(0.0, x))
        predictions_real['Adjusted R-Squared'] = predictions_real['Adjusted R-Squared'].apply(lambda x: max(0.0, x))
        predictions_synthetic['Adjusted R-Squared'] = predictions_synthetic['Adjusted R-Squared'].apply(lambda x: max(0.0, x))


        predictions_real = add_summary_classifier(predictions_real)
        predictions_synthetic = add_summary_classifier(predictions_synthetic)
        machine_learning_dict['real']['predictions'] = predictions_real
        machine_learning_dict['synthetic']['predictions'] = predictions_synthetic
        machine_learning_dict['difference'] = calculate_differences_as_dict(machine_learning_dict)
    else:
        print('Classification Activated')
        # Encode labels consistently
        le = LabelEncoder()
        all_labels = pd.concat([y_train, y_test])
        le.fit(all_labels)
        
        y_train_encoded = le.transform(y_train)
        y_test_encoded = le.transform(y_test)
        
        # Also encode real training/testing data
        y_train_real_encoded = le.transform(y_train_real)
        y_test_real_encoded = le.transform(y_test_real)
        
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


def discriminator_based_evaluation(real: pd.DataFrame, synthetic: pd.DataFrame, train_size: float) -> dict:
    """
    Evaluate synthetic data quality using discriminator-based approach with balanced datasets.

    Args:
        real (pd.DataFrame): Real dataset
        synthetic (pd.DataFrame): Synthetic dataset
        train_size (float): Proportion of data to use for training
        random_state (int): Random seed for reproducibility

    Returns:
        dict: Dictionary containing evaluation metrics with color coding
    """
    test_size = 1 - train_size
    random_state = 42
    cols_to_drop = []

    # Identify columns with 100% NA in the real dataset
    na_cols = real.columns[real.isna().all()].tolist()
    cols_to_drop.extend(na_cols)

    # Identify categorical columns with 100% unique values in the real dataset
    categorical_cols_real = real.select_dtypes(include=['object', 'category']).columns
    for col in categorical_cols_real:
        if col not in cols_to_drop: # Avoid re-checking already marked columns
            if real[col].nunique(dropna=False) == len(real): # dropna=False to consider NAs as a unique value if present
                cols_to_drop.append(col)

    # Identify columns with 100% NA in the real dataset
    na_cols_synthetic = synthetic.columns[synthetic.isna().all()].tolist()
    for col_synth in na_cols_synthetic:
         cols_to_drop.append(col_synth)

    categorical_cols_synthetic = synthetic.select_dtypes(include=['object', 'category']).columns
    for col_synth in categorical_cols_synthetic:
        if col_synth not in cols_to_drop:
            if synthetic[col].nunique(dropna=False) == len(real): # dropna=False to consider NAs as a unique value if present
                cols_to_drop.append(col)

    # Ensure unique columns to drop
    cols_to_drop = list(set(cols_to_drop))

    if cols_to_drop:
        print(f"Dropping columns from real and synthetic datasets: {cols_to_drop}")
        real = real.drop(columns=cols_to_drop)
        synthetic = synthetic.drop(columns=[col for col in cols_to_drop if col in synthetic.columns])

    # Balance datasets by sampling from the larger one
    min_size = min(len(real), len(synthetic))
    if len(real) > min_size:
        real = real.sample(n=min_size, random_state=random_state)
    elif len(synthetic) > min_size:
        synthetic = synthetic.sample(n=min_size, random_state=random_state)

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
        X, y, test_size=test_size, random_state=random_state
    )

    # Train and evaluate classifiers
    filtered_classifiers = [clf for clf in CLASSIFIERS if clf[0] in VALID_CLASSIFIERS]
    lazy = LazyClassifier(verbose=0, ignore_warnings=False, custom_metric=None, classifiers=filtered_classifiers)
    models, predictions = lazy.fit(X_train, X_test, y_train, y_test)
    predictions = remove_roc_auc(predictions.to_dict())
    predictions = add_summary_classifier(predictions)

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
        impute_val = 0 if pd.isna(mean_val) else mean_val
        df_imputed[col] = df_imputed[col].fillna(impute_val)

    categorical_cols = df_imputed.select_dtypes(exclude=['number']).columns
    for col in categorical_cols:
        df_imputed[col] = df_imputed[col].astype(str)  
        df_imputed[col] = df_imputed[col].fillna(missing_value_placeholder)
    
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