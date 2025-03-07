import glob

import pandas as pd

from utils import iso_to_strftime, parse_to_date_format, adjust_date_within_bounds_post
from utils import BOOLEAN_MAP


def post_process_dataframe(df: pd.DataFrame, config):
    # Replace "missing_value" with NaN
    df = df.replace("missing_value", pd.NA)

    # Also replace empty strings and common missing value indicators
    df = df.replace({"": pd.NA, "null": pd.NA, "NULL": pd.NA, "NaN": pd.NA, "nan": pd.NA})

    for column_config in config:
        column_name = column_config['name']
        column_type = column_config['type']

        if column_type == 'STRING':
            df[column_name] = df[column_name].astype(dtype='string')
            continue

        if column_type == 'BOOLEAN':
            df[column_name] = df[column_name].astype(str)
            df[column_name] = df[column_name].map(BOOLEAN_MAP).astype('boolean')
            continue

        if column_type == 'INTEGER':
            df[column_name] = pd.to_numeric(df[column_name], errors='coerce')
            df[column_name] = df[column_name].round().astype('Int64')

        if column_type == 'DECIMAL':
            df[column_name] = pd.to_numeric(df[column_name], errors='coerce').astype('Float64')

        if column_type == 'DATE':
            date_format = next(
                (cfg.get('dateFormatter') or cfg.get('dateTimeFormatter') for cfg in column_config['configurations']),
                None)
            process_date(df, column_name, date_format)

    return df


def process_date(df, column_name, date_format):
    df[column_name] = pd.to_numeric(df[column_name], errors='coerce')
    df[column_name] = df[column_name].apply(adjust_date_within_bounds_post)
    date_format = iso_to_strftime(date_format)
    df[column_name] = df[column_name].apply(parse_to_date_format, args=(date_format,))
    df[column_name] = df[column_name].astype(dtype='string')


# NOTE: Adapted from synthetization module to post_process the heart testdata since in the split data, the datetime
# format did not match with the synthetic and original data


if __name__ == "__main__":
    for file in glob.glob("../inputs/*/*.xlsx"):
        df = pd.read_excel(file)
        if "birthdate" in df.columns and pd.api.types.is_numeric_dtype(df["birthdate"]):
            process_date(df, "birthdate", "yyyy-MM-dd")
            print("hello")
        if "death_date" in df.columns and pd.api.types.is_numeric_dtype(df["death_date"]):
            process_date(df, "death_date", "yyyy-MM-dd")
            print("hello")
        df.to_csv(file.replace(".xlsx", ".csv"), index=False)
