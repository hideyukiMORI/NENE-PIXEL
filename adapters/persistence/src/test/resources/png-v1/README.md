# PNG policy v1 golden fixtures

These fixtures were constructed independently with Python `struct`, `binascii.crc32`, and
`zlib.adler32` from the PNG/RFC wire contract in ADR 0019; the Kotlin encoder was not used.
Both have IHDR/sRGB(intent 1)/IDAT/IEND, filter None, and one stored DEFLATE block per row.
Golden equality pins signature, byte order, chunk order, zlib framing, and all checksums.
JDK ImageIO independently checks decoded dimensions and every straight RGBA value.

- `minimal.png`: 1 x 1, row-major packed RGBA `11223344`.
- `transparent-rectangle.png`: 3 x 2, rows `10203000 abcdef01 1122337f` and
  `deadbefe 010203ff 00000000`; includes hidden RGB and alpha 0, 1, 127, 254, 255.

| Fixture | Bytes | SHA-256 |
| --- | --- | --- |
| `minimal.png` | 86 | `4d3a1cf1fc32a22cec39aba2796b0bb810531ef63b1e62976a5343861223ee71` |
| `transparent-rectangle.png` | 112 | `50cf2dafeac9131789b7d51e8a8b923a530cf8135d230ada26a2eae1a83d6661` |
