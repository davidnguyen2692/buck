""" Functions for BUCK file in this package """

def get_release_version():
    """ Gets the release version from the command line or "" """
    return native.read_config("buck", "release_version", "")

def get_release_timestamp():
    """ Gets the release version from the command line or "" """
    return native.read_config("buck", "release_timestamp" ,"")

def get_gen_buck_info_command(gen_buck_info_target):
    """
    Gets the gen_buck_info command to run based on configuration

    Args:
        gen_buck_info_target: The target that contains gen_buck_info script

    Returns:
        The cmd string to run
    """
    version = get_release_version()
    timestamp = get_release_timestamp()
    if version and timestamp:
        return (
            '$(exe {target}) --release-version {version} ' +
            '--release-timestamp {timestamp} > "$OUT"'
        ).format(target=gen_buck_info_target, version=version, timestamp=timestamp)
    else:
        return "$(exe {target}) > $OUT".format(target=gen_buck_info_target)
