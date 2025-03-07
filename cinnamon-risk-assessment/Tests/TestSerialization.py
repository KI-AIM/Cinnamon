import unittest
import pandas as pd
import numpy as np

from risk_assessment.RiskAssessmentProcess import make_serializable


class TestSerialization(unittest.TestCase):

    def test_dataframe_conversion(self):
        """Test conversion of a pandas DataFrame to a list of dictionaries."""
        df = pd.DataFrame({'col1': [1, 2], 'col2': ['a', 'b']})
        expected_output = [{'col1': 1, 'col2': 'a'}, {'col1': 2, 'col2': 'b'}]
        result = make_serializable(df)
        self.assertEqual(result, expected_output)

    def test_nested_dict_conversion(self):
        """Test nested dictionary with DataFrame and NumPy integers."""
        df = pd.DataFrame({'value': [1, 2]})
        data = {
            'id': np.int64(100),
            'details': {
                'name': 'test',
                'dataframe': df
            }
        }
        expected_output = {
            'id': 100,
            'details': {
                'name': 'test',
                'dataframe': [{'value': 1}, {'value': 2}]
            }
        }
        result = make_serializable(data)
        self.assertEqual(result, expected_output)

    def test_list_conversion(self):
        """Test conversion of a list containing mixed types including DataFrames and NumPy integers."""
        df = pd.DataFrame({'num': [10, 20]})
        data = [
            np.int32(42),
            df,
            {'nested': np.int64(99)}
        ]
        expected_output = [
            42,
            [{'num': 10}, {'num': 20}],
            {'nested': 99}
        ]
        result = make_serializable(data)
        self.assertTrue(isinstance(result[2]["nested"], int))
        self.assertEqual(result, expected_output)

    def test_numpy_integer_conversion(self):
        """Test conversion of NumPy integer types to native Python int."""
        input_data = np.int64(12345)
        expected_output = 12345
        result = make_serializable(input_data)
        self.assertEqual(result, expected_output)

    def test_no_conversion_needed(self):
        """Test that native Python types are returned unchanged."""
        input_data = {'key': 'value', 'number': 42, 'list': [1, 2, 3]}
        result = make_serializable(input_data)
        self.assertEqual(result, input_data)


if __name__ == "__main__":
    unittest.main()
