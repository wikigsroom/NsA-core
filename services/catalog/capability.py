"""Match declared release requirements to measured capabilities, including offline parity."""
import re


def meets_minimum(device: dict, minimum: dict) -> bool:
    if device.get('api_level', 0) < minimum['api_level'] or device.get('abi') != minimum['abi']:
        return False
    for key in ('page_size_bytes', 'gpu_family', 'gpu_driver_digest'):
        if key in minimum and device.get(key) != minimum[key]:
            return False
    if 'ram_bytes' in minimum and ('ram_bytes' not in device or int(device['ram_bytes']) < int(minimum['ram_bytes'])):
        return False
    if 'vulkan_api' in minimum:
        def version(text):
            if not isinstance(text, str) or not re.fullmatch(r'[0-9]+(?:\.[0-9]+){1,2}', text):
                return None
            parts = tuple(map(int, text.split('.')))
            return parts + (0,) * (3 - len(parts))
        actual, required = version(device.get('vulkan_api')), version(minimum['vulkan_api'])
        if actual is None or required is None or actual < required:
            return False
    return set(minimum.get('features', ())) <= set(device.get('features', ()))
