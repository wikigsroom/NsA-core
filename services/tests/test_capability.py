import pytest
from catalog.capability import meets_minimum


@pytest.mark.parametrize('change,expected', [
    ({}, True), ({'ram_bytes': '4096'}, False), ({'page_size_bytes': 16384}, False),
    ({'vulkan_api': '1.1.99'}, False), ({'vulkan_api': '1.2'}, True),
    ({'vulkan_api': 'unknown'}, False), ({'features': []}, False),
    ({'gpu_family': 'different'}, False), ({'api_level': 30}, False),
    ({'abi': 'x86_64'}, False), ({'gpu_driver_digest': 'b' * 64}, False),
])
def test_required_capabilities_cannot_be_bypassed(change, expected):
    minimum = {'api_level': 31, 'abi': 'arm64-v8a', 'ram_bytes': '8192', 'page_size_bytes': 4096,
               'gpu_family': 'measured GPU', 'gpu_driver_digest': 'a' * 64,
               'vulkan_api': '1.2.0', 'features': ['shaderInt64']}
    actual = {**minimum, 'api_level': 32, 'features': ['shaderInt64', 'geometryShader'], **change}
    assert meets_minimum(actual, minimum) == expected
    for field in ('ram_bytes', 'page_size_bytes', 'gpu_family', 'gpu_driver_digest', 'vulkan_api', 'features'):
        assert not meets_minimum({k: v for k, v in minimum.items() if k != field}, minimum)
