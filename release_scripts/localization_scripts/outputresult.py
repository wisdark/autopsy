from typing import List, Union, TypedDict


class ColumnStyle(TypedDict):
    """
    Describes style for each cell in a column.
    """
    width: int
    wrap_text: bool


class OutputResult:
    """
    Describes a result that is ready to be written to file(s).
    """
    column_styles: List[ColumnStyle]
    results: List[List[str]]
    omitted: Union[List[List[str]], None]
    deleted: Union[List[List[str]], None]

    def __init__(self, results: List[List[str]], omitted: Union[List[List[str]], None] = None,
                 deleted: Union[List[List[str]], None] = None, style: Union[List[ColumnStyle], None] = None):
        """
        Constructs a ProcessingResult.
        Args:
            results: Items to be written as results.  Data will be written such that the item at row,cell will be
            located within result at results[row][col].
            omitted: Items to be written as omitted.  Data will be written such that the item at row,cell will be
            located within result at results[row][col].
            deleted: Items to be written as omitted.  Data will be written such that the item at row,cell will be
            located within result at results[row][col].
            style: Style for each column.  No column formatting will happen for null.
        """

        self.results = results
        self.omitted = omitted
        self.deleted = deleted
        self.column_styles = style
