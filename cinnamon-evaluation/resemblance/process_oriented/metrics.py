import pm4py
import pandas as pd


def event_distribution(real: pd.DataFrame, synthetic: pd.DataFrame, event_column: str) -> dict:
    """
    Return the count of each event type in the real and synthetic data as a dictionary.

    Parameters:
    real: pd.DataFrame - A DataFrame containing the real event data.
    synthetic: pd.DataFrame - A DataFrame containing the synthetic event data.
    event_column: str - The name of the column in the DataFrame that contains the event names.

    Returns:
    dict: A dictionary with 'real' and 'synthetic' as keys and value counts of events as values.
    """
    distribution = {'real': {}, 'synthetic': {}}

    count_real = real[event_column].value_counts()
    count_synthetic = synthetic[event_column].value_counts()

    distribution['real'] = count_real.to_dict()
    distribution['synthetic'] = count_synthetic.to_dict()

    return distribution


def calculate_trace_length_distribution(real: pd.DataFrame, synthetic: pd.DataFrame, case_identifier_column: str) -> (
        dict):
    """
    Return the count of each trace length in the real and synthetic data as a dictionary.

    Parameters:
    real: pd.DataFrame - A DataFrame containing the real event data.
    synthetic: pd.DataFrame - A DataFrame containing the synthetic event data.
    case_identifier_column: str - The name of the column in the DataFrame that contains the case identifiers.

    Returns:
    dict: A dictionary with 'real' and 'synthetic' as keys and value counts of trace lengths as values.
    """
    distribution = {'real': {}, 'synthetic': {}}

    count_real = real[case_identifier_column].value_counts()
    count_real = count_real.value_counts().sort_index()
    count_real.index = pd.to_numeric(count_real.index)
    distribution['real'] = count_real.to_dict()

    count_synthetic = synthetic[case_identifier_column].value_counts()
    count_synthetic = count_synthetic.value_counts().sort_index()
    count_synthetic.index = pd.to_numeric(count_synthetic.index)
    distribution['synthetic'] = count_synthetic.to_dict()

    return distribution


def calculate_throughput_time(real: pd.DataFrame, synthetic: pd.DataFrame):
    """
    Calculate the throughput time of real and synthetic PM4Py event logs.
    The throughput time is defined as the time between the first and the last event of a trace.

    Parameters:
    real: pd.DataFrame - A DataFrame containing the real event data.
    synthetic: pd.DataFrame - A DataFrame containing the synthetic event data.

    Returns:
    dict: A dictionary with 'real' and 'synthetic' as keys and throughput times as values.
    """
    real = real.dropna(subset=['time:timestamp'])
    log_real = pm4py.convert_to_event_log(real)
    all_case_durations_real = pm4py.get_all_case_durations(log_real)

    synthetic = synthetic.dropna(subset=['time:timestamp'])
    log_synthetic = pm4py.convert_to_event_log(synthetic)
    all_case_durations_synthetic = pm4py.get_all_case_durations(log_synthetic)

    throughput_times = {
        'real': all_case_durations_real,
        'synthetic': all_case_durations_synthetic
    }

    return throughput_times


def calculate_start_event_distribution(real: pd.DataFrame, synthetic: pd.DataFrame, case_identifier_column: str,
                                       event_column: str) -> dict:
    """
    Return the count of each start event type in the real and synthetic PM4Py event logs as a dictionary.

    Parameters:
    real: pd.DataFrame - A DataFrame containing the real event data.
    synthetic: pd.DataFrame - A DataFrame containing the synthetic event data.
    case_identifier_column: str - The name of the column in the DataFrame that contains the case identifiers.
    event_column: str - The name of the column in the DataFrame that contains the event names.

    Returns:
    dict: A dictionary with 'real' and 'synthetic' as keys and value counts of start events as values.
    """
    start_events_real = real.groupby(case_identifier_column).first()[event_column].value_counts()
    start_events_synthetic = synthetic.groupby(case_identifier_column).first()[event_column].value_counts()

    start_event_distribution = {
        'real': start_events_real.to_dict(),
        'synthetic': start_events_synthetic.to_dict()
    }

    return start_event_distribution


def calculate_end_event_distribution(real: pd.DataFrame, synthetic: pd.DataFrame, case_identifier_column: str,
                                     event_column: str) -> dict:
    """
    Return the count of each end event type in the real and synthetic PM4Py event logs as a dictionary.

    Parameters:
    real: pd.DataFrame - A DataFrame containing the real event data.
    synthetic: pd.DataFrame - A DataFrame containing the synthetic event data.
    case_identifier_column: str - The name of the column in the DataFrame that contains the case identifiers.
    event_column: str - The name of the column in the DataFrame that contains the event names.

    Returns:
    dict: A dictionary with 'real' and 'synthetic' as keys and value counts of end events as values.
    """
    end_events_real = real.groupby(case_identifier_column).last()[event_column].value_counts()

    end_events_synthetic = synthetic.groupby(case_identifier_column).last()[event_column].value_counts()

    end_event_distribution = {
        'real': end_events_real.to_dict(),
        'synthetic': end_events_synthetic.to_dict()
    }

    return end_event_distribution


def calculate_trace_variant_distribution(real: pd.DataFrame, synthetic: pd.DataFrame, case_identifier_column: str,
                                         event_column: str, timestamp_column: str) -> dict:
    """
    Return the count of each trace variant in the real and synthetic PM4Py event logs as a dictionary.

    Parameters:
    real: pd.DataFrame - A DataFrame containing the real event data.
    synthetic: pd.DataFrame - A DataFrame containing the synthetic event data.
    case_identifier_column: str - The name of the column in the DataFrame that contains the case identifiers.
    event_column: str - The name of the column in the DataFrame that contains the event names.
    timestamp_column: str - The name of the column in the DataFrame that contains the timestamps.

    Returns:
    dict: A dictionary with 'real' and 'synthetic' as keys and value counts of trace variants as values.
    """
    trace_variants_real = pm4py.get_variants(real, activity_key=event_column, case_id_key=case_identifier_column,
                                             timestamp_key=timestamp_column)

    trace_variants_synthetic = pm4py.get_variants(synthetic, activity_key=event_column,
                                                  case_id_key=case_identifier_column, timestamp_key=timestamp_column)

    trace_variant_distribution = {
        'real': {variant: len(cases) for variant, cases in trace_variants_real.items()},
        'synthetic': {variant: len(cases) for variant, cases in trace_variants_synthetic.items()}
    }

    return trace_variant_distribution
