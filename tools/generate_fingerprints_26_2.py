#!/usr/bin/env python3
"""Build the bundled room fingerprint database from roomscan captures."""

import argparse
import json
from pathlib import Path


def registry_name(state):
    return state.split("[", 1)[0]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("captures", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    captures = []
    observed = set()
    for path in sorted(args.captures.glob("*.json")):
        data = json.loads(path.read_text())
        if not data.get("identified", False):
            continue
        palette = [registry_name(state) for state in data["palette"]]
        observed.update(palette)
        blocks = [(x, y, z, palette[index]) for x, y, z, index in data["blocks"]]
        captures.append((data, blocks))

    palette = sorted(observed)
    palette_index = {name: index for index, name in enumerate(palette)}
    entries = []
    for data, blocks in captures:
        # The live matcher samples inset even x/z columns. Keep every observed block on
        # those columns, then take a stable spread so reverse verification stays cheap.
        def doorway(block):
            x, y, z, _ = block
            if not 0 <= y <= 5:
                return False
            lx = x % 32
            lz = z % 32
            return (13 <= lx <= 17 and (lz <= 2 or lz >= 28)) or \
                   (13 <= lz <= 17 and (lx <= 2 or lx >= 28))

        sampled = [block for block in blocks
                   if 2 <= block[0] <= data["sizeX"] - 2
                   and 2 <= block[2] <= data["sizeZ"] - 2
                   and block[0] % 2 == 0 and block[2] % 2 == 0
                   and not doorway(block)]
        sampled.sort(key=lambda b: ((b[0] * 73856093) ^ (b[1] * 19349663) ^
                                    (b[2] * 83492791) ^ palette_index[b[3]],
                                    b[1], b[0], b[2]))
        points = sampled[:768]
        points.sort(key=lambda b: (b[1], b[0], b[2], b[3]))
        entries.append({
            "name": data["name"].lower(),
            "sizeX": data["sizeX"],
            "sizeZ": data["sizeZ"],
            "blocks": [[x, y, z, palette_index[name]] for x, y, z, name in points],
        })

    output = {
        "format": 1,
        "minecraft": "26.2",
        "coordinateSystem": "room-relative, de-rotated NW anchor, floor y=0",
        "palette": palette,
        "rooms": entries,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, separators=(",", ":")) + "\n")
    print(f"wrote {len(entries)} rooms, {len(palette)} observed blocks to {args.output}")


if __name__ == "__main__":
    main()
