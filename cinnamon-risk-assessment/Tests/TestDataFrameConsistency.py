import unittest

import pandas as pd

from base_assessment.general_eval.datatype_consistancy_eval import check_column_name_consistency, \
    check_dtype_consistency, df_consistency_eval


class TestDataFrameConsistency(unittest.TestCase):
    def setUp(self):
        self.df1 = pd.DataFrame({
            "A": [1, 2, 3],
            "B": [4.0, 5.1, 6.2],
            "C": ["x", "y", "z"]
        })

        self.df2 = pd.DataFrame({
            "A": [1, 2, 3],
            "B": [4, 5, 6],
            "C": ["x", "y", "z"]
        })

        self.df3 = pd.DataFrame({
            "B": [4.0, 5.1, 6.2],
            "C": ["x", "y", "z"]
        })

    def test_column_name_consistency(self):
        data_frames = [self.df1, self.df3]
        result = check_column_name_consistency(data_frames)
        self.assertFalse(result)

        result = check_column_name_consistency(data_frames, try_correction=True)
        self.assertTrue(result)

    def test_dtype_consistency(self):
        data_frames = [self.df1, self.df2]
        result = check_dtype_consistency(data_frames)
        self.assertFalse(result)

        result = check_dtype_consistency(data_frames, try_correction=True)
        self.assertTrue(result)

    def test_df_consistency_eval(self):
        data_frames = [self.df1, self.df2, self.df3]
        df_consistency_eval(data_frames, try_correction=True)

        self.assertTrue(data_frames[1].columns.equals(self.df1.columns))
        self.assertTrue((data_frames[1].dtypes == self.df1.dtypes).all())

        self.assertTrue(data_frames[2].columns.equals(self.df1.columns))
        self.failureException((data_frames[2].dtypes == self.df1.dtypes).all())


if __name__ == '__main__':
    unittest.main()
