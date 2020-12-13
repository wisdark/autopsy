"""This script determines the updated, added, and deleted properties from the '.properties-MERGED' files
and generates a csv file containing the items changed.  : gitpython, jproperties, pyexcel-xlsx, xlsxwriter and pyexcel.
As a consequence, it also requires git >= 1.7.0 and python >= 3.4.
"""
import sys
from envutil import get_proj_dir
from excelutil import write_results_to_xlsx
from gitutil import get_property_files_diff, get_git_root, get_commit_id
from itemchange import convert_to_output
from csvutil import write_results_to_csv
import argparse
from langpropsutil import get_commit_for_language, LANG_FILENAME
from outputtype import OutputType


def main():
    # noinspection PyTypeChecker
    parser = argparse.ArgumentParser(description="Determines the updated, added, and deleted properties from the "
                                                 "'.properties-MERGED' files and generates a csv file containing "
                                                 "the items changed.",
                                     formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument(dest='output_path', type=str, help='The path to the output file.  The output path should '
                                                           'be specified as a relative path with the dot slash notation'
                                                           ' (i.e. \'./outputpath.xlsx\') or an absolute path.')

    parser.add_argument('-r', '--repo', dest='repo_path', type=str, required=False,
                        help='The path to the repo.  If not specified, path of script is used.')
    parser.add_argument('-fc', '--first-commit', dest='commit_1_id', type=str, required=False,
                        help='The commit for previous release.  This flag or the language flag need to be specified'
                             ' in order to determine a start point for the difference.')
    parser.add_argument('-lc', '--latest-commit', dest='commit_2_id', type=str, default='HEAD', required=False,
                        help='The commit for current release.')
    parser.add_argument('-nc', '--no-commits', dest='no_commits', action='store_true', default=False,
                        required=False, help="Suppresses adding commits to the generated csv header.")
    parser.add_argument('-o', '--output-type', dest='output_type', type=OutputType, choices=list(OutputType),
                        required=False, help="The output type.  Currently supports 'csv' or 'xlsx'.", default='xlsx')
    parser.add_argument('-l', '--language', dest='language', type=str, default=None, required=False,
                        help='Specify the language in order to determine the first commit to use (i.e. \'ja\' for '
                             'Japanese.  This flag overrides the first-commit flag.')

    parser.add_argument('-nt', '--no-translated-col', dest='no_translated_col', action='store_true', default=False,
                        required=False, help="Don't include a column for translation.")

    args = parser.parse_args()
    repo_path = args.repo_path if args.repo_path is not None else get_git_root(get_proj_dir())
    output_path = args.output_path
    commit_1_id = args.commit_1_id
    output_type = args.output_type
    show_translated_col = not args.no_translated_col

    lang = args.language
    if lang is not None:
        commit_1_id = get_commit_for_language(lang)

    if commit_1_id is None:
        print('Either the first commit or language flag need to be specified.  If specified, the language file, ' +
              LANG_FILENAME + ', may not have the latest commit for the language.', file=sys.stderr)
        parser.print_help(sys.stderr)
        sys.exit(1)

    commit_2_id = args.commit_2_id
    show_commits = not args.no_commits

    changes = get_property_files_diff(repo_path, commit_1_id, commit_2_id)
    processing_result = convert_to_output(changes,
                                          commit1_id=get_commit_id(repo_path, commit_1_id) if show_commits else None,
                                          commit2_id=get_commit_id(repo_path, commit_2_id) if show_commits else None,
                                          show_translated_col=show_translated_col,
                                          separate_deleted=True)

    # based on https://stackoverflow.com/questions/60208/replacements-for-switch-statement-in-python
    {
        OutputType.csv: write_results_to_csv,
        OutputType.xlsx: write_results_to_xlsx
    }[output_type](processing_result, output_path)

    sys.exit(0)


if __name__ == "__main__":
    main()
