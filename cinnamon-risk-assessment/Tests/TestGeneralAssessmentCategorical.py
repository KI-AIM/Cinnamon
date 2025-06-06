import unittest
import pandas as pd

from base_assessment.general_eval.categorical_attribute_eval import rare_categories, number_of_categorical_attributes, \
    unique_combinations_of_categorical_attributes, identify_columns_with_unique_entries


class TestCategoricalFunctions(unittest.TestCase):

    def setUp(self):
        """Prepare sample data for testing."""
        self.df = pd.DataFrame({
            'A': ['a', 'b', 'a', 'c', 'a'],
            'B': ['x', 'y', 'x', 'y', 'x'],
            'C': ['cat1', 'cat2', 'cat3', 'cat1', 'cat2'],
            'D': ['u1', 'u2', 'u3', 'u4', 'u5'],  # Unique entries
            'E': ['same', 'same', 'same', 'same', 'same'],  # Constant column
            'F': [1, 2, 3, 4, 5],  # Non-categorical
        })

    def test_rare_categories(self):
        """Test rare_categories function."""
        column = self.df['A']
        has_rare, rare_list = rare_categories(column, threshold=0.2)

        self.assertTrue(has_rare, "Rare categories should be detected.")
        self.assertIn('b', rare_list, "'b' should be identified as a rare category.")
        self.assertIn('c', rare_list, "'c' should be identified as a rare category.")
        self.assertNotIn('a', rare_list, "'a' should not be identified as rare.")

    def test_rare_categories_no_rare(self):
        """Test rare_categories when no rare categories exist."""
        column = self.df['B']
        has_rare, rare_list = rare_categories(column, threshold=0.1)

        self.assertFalse(has_rare, "No rare categories should be detected.")
        self.assertEqual(len(rare_list), 0, "Rare categories list should be empty.")

    def test_number_of_categorical_attributes(self):
        """Test number_of_categorical_attributes function."""
        count = number_of_categorical_attributes(self.df)
        self.assertEqual(count, 5, "There should be 5 categorical columns in the DataFrame.")

    def test_unique_combinations_of_categorical_attributes(self):
        """Test unique_combinations_of_categorical_attributes function."""
        combinations, unique_df = unique_combinations_of_categorical_attributes(self.df[["A", "B"]])

        self.assertEqual(combinations, 2, "There should be 2 unique combination.")
        self.assertIsInstance(unique_df, pd.DataFrame, "Result should be a DataFrame.")

    def test_identify_columns_with_unique_entries(self):
        """Test identify_columns_with_unique_entries function."""
        unique_columns = identify_columns_with_unique_entries(self.df)

        self.assertIn('D', unique_columns, "'D' column should be identified as having unique entries.")
        self.assertNotIn('A', unique_columns, "'A' column does not have unique entries.")
        self.assertNotIn('E', unique_columns, "'E' column has constant values and should not be included.")

    def test_identify_columns_with_unique_entries_no_uniques(self):
        """Test identify_columns_with_unique_entries with no unique columns."""
        df_no_unique = self.df.copy()
        df_no_unique['D'] = ['u1', 'u1', 'u1', 'u1', 'u1']  # All values the same
        unique_columns = identify_columns_with_unique_entries(df_no_unique)

        self.assertEqual(len(unique_columns), 0, "No columns should be identified as having unique entries.")


if __name__ == '__main__':
    unittest.main()
