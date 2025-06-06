import unittest
import pandas as pd
import numpy as np

from base_assessment.general_eval.continuous_attribute_eval import outlier_records, number_of_continuous_attributes, \
    unique_combinations_of_continuous_attributes, identify_columns_with_unique_entries


class TestGeneralAssessment__continuous(unittest.TestCase):

    def setUp(self):
        """Prepare sample data for testing."""
        self.df = pd.DataFrame({
            'A': [1, 2, 3, 4, 5],
            'B': [10, 10, 10, 10, 10],
            'C': [1.1, 2.2, 2.3, 4.4, 5.5],
            'D': [1, 1, 1, 1, 1],
            'E': [1, 2, 3, 4, 5],
            'F': ['x', 'y', 'z', 'x', 'y']
        })

        self.df_with_outliers = pd.DataFrame({
            'A': [1, 2, 3, 4, 200],
            'B': [50, 60, 70, 1000, 2000],
            'C': [0.1, 0.2, 0.3, 50, 60]
        })

    def test_outlier_records(self):
        """Test outlier_records method."""
        column = self.df_with_outliers['A']
        has_outliers, outlier_ids = outlier_records(column, threshold_factor=1.5)

        self.assertTrue(has_outliers, "Outliers should be detected.")
        self.assertGreater(len(outlier_ids), 0, "Outlier IDs list should not be empty.")

    def test_outlier_records_no_outliers(self):
        """Test outlier_records when no outliers exist."""
        column = self.df['A']
        has_outliers, outlier_ids = outlier_records(column, threshold_factor=1.5)

        self.assertFalse(has_outliers, "No outliers should be detected.")
        self.assertEqual(len(outlier_ids), 0, "Outlier IDs list should be empty.")

    def test_number_of_continuous_attributes(self):
        """Test number_of_continuous_attributes function."""
        count = number_of_continuous_attributes(self.df)
        self.assertEqual(count, 5, "There should be 5 continuous columns in the DataFrame.")

    def test_unique_combinations_of_continuous_attributes(self):
        """Test unique_combinations_of_continuous_attributes function."""
        combinations, unique_df = unique_combinations_of_continuous_attributes(self.df, rounding=False)

        self.assertEqual(combinations, 5, "There should be 5 unique combinations in the DataFrame.")
        self.assertIsInstance(unique_df, pd.DataFrame, "Result should be a DataFrame.")

    def test_unique_combinations_with_rounding(self):
        """Test unique_combinations_of_continuous_attributes with rounding."""
        rounded_df = self.df.copy()
        rounded_df['C'] = rounded_df['C'].round(0)

        combinations, unique_df = unique_combinations_of_continuous_attributes(rounded_df, rounding=True, decimals=0)

        self.assertEqual(combinations, 5, "Rounded combinations count should be correct.")

    def test_identify_columns_with_unique_entries(self):
        """Test identify_columns_with_unique_entries function."""
        unique_columns = identify_columns_with_unique_entries(self.df, rounding=False)

        self.assertIn('A', unique_columns, "'A' column has unique values.")
        self.assertIn('C', unique_columns, "'C' column has unique values.")
        self.assertNotIn('B', unique_columns, "'B' column does not have unique values.")

    def test_identify_columns_with_unique_entries_with_rounding(self):
        """Test identify_columns_with_unique_entries with rounding."""
        rounded_df = self.df.copy()

        unique_columns = identify_columns_with_unique_entries(rounded_df, rounding=True, decimals=0)

        self.assertIn('A', unique_columns, "'A' column remains unique after rounding.")
        self.assertNotIn('B', unique_columns, "'B' column is no longer unique after rounding.")
        self.assertNotIn('C', unique_columns, "'C' column is no longer unique after rounding.")


if __name__ == '__main__':
    unittest.main()
