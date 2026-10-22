"""Android R+ resource-table installation requirements, beyond signature validity."""
import struct
import zipfile
from pathlib import Path


def check_resource_table(apk):
    """Require one uncompressed, 4-byte-aligned resources.arsc in the final ZIP.

    ZIP's central directory can report a valid file while Android cannot mmap its
    local data. Calculate the offset from the local header, not the central extra.
    """
    path = Path(apk)
    with zipfile.ZipFile(path) as archive:
        resources = [info for info in archive.infolist() if info.filename == 'resources.arsc']
        if len(resources) != 1:
            raise ValueError('APK must contain exactly one resources.arsc')
        info = resources[0]
        if info.compress_type != zipfile.ZIP_STORED:
            raise ValueError('resources.arsc must be uncompressed (ZIP_STORED) for Android 11+ targets')
        with path.open('rb') as source:
            source.seek(info.header_offset)
            header = source.read(30)
        if len(header) != 30 or header[:4] != b'PK\x03\x04':
            raise ValueError('Invalid resources.arsc local ZIP header')
        compression = struct.unpack_from('<H', header, 8)[0]
        name_length, extra_length = struct.unpack_from('<HH', header, 26)
        if compression != zipfile.ZIP_STORED:
            raise ValueError('resources.arsc local ZIP compression differs from ZIP_STORED')
        offset = info.header_offset + 30 + name_length + extra_length
        if offset % 4:
            raise ValueError('resources.arsc data must be aligned to 4 bytes; offset=' + str(offset))
        return {'compression': 'stored', 'dataOffset': offset, 'alignment': 4}
