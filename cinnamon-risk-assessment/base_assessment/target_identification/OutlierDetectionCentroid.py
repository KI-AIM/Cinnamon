import pandas as pd
import numpy as np
from sklearn.preprocessing import MinMaxScaler
from scipy.spatial.distance import cdist


class OutlierDetectionCentroid:
    def __init__(self, dataset: pd.DataFrame):
        self.dataset = dataset
        self.continuous_columns = dataset.select_dtypes("number").columns
        self.categorical_columns = dataset.select_dtypes(include=["object", "category"]).columns
        self.centroid = None
        self.normalized_distances = None

    def preprocess_data(self):
        # Scale continuous columns
        scaler = MinMaxScaler()
        df_continuous = pd.DataFrame(scaler.fit_transform(self.dataset[self.continuous_columns]),
                                     columns=self.continuous_columns)

        # Replace categorical values by their normalized frequencies
        df_categorical = self.dataset[self.categorical_columns]
        df_categorical_normalized_freq = pd.DataFrame(index=df_categorical.index)
        for column in df_categorical.columns:
            frequencies = df_categorical[column].value_counts(normalize=True)
            df_categorical_normalized_freq[column] = df_categorical[column].map(frequencies)

        # Combine continuous and normalized frequency dataframes for the complete data
        self.df_combined = pd.concat([df_continuous, df_categorical_normalized_freq], axis=1)

    def calculate_centroid(self):
        centroid_continuous = self.df_combined[self.continuous_columns].mean()
        centroid_categorical = self.df_combined[self.categorical_columns].max()

        self.centroid = pd.concat([centroid_continuous, centroid_categorical])

    def calculate_distances(self):
        # Convert centroid to the correct shape for distance calculation
        centroid_array = self.centroid.values.reshape(1, -1)

        # Calculate Euclidean distances
        distances = cdist(self.df_combined, centroid_array, 'euclidean').flatten()

        # Normalize distances to range from 0 to 1
        self.normalized_distances = (distances - distances.min()) / (distances.max() - distances.min())

    def add_distances_to_dataset(self):
        # Adding normalized distances to the original dataframe
        self.dataset['NormalizedDistanceToCentroid'] = self.normalized_distances

    def run(self):
        """Run the entire outlier detection process."""
        self.preprocess_data()
        self.calculate_centroid()
        self.calculate_distances()
        self.add_distances_to_dataset()

# Example usage:
# detector = OutlierDetectionCentroid(dataset, continuous_columns, categorical_columns)
# detector.run()
# Access the dataset with distances: detector.dataset
