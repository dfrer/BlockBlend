"""Offline regressions for the public publication boundary."""
import io
import json
import struct
import unittest
import zipfile
import zlib

from release_check import PublicationError, check_public_files, check_mod_jar


class PublicationBoundaryTest(unittest.TestCase):
    def test_private_world_cannot_be_published(self):
        with self.assertRaises(PublicationError) as rejected:
            check_public_files({"run/saves/private/level.dat": b"private fixture"})
        self.assertNotIn("private fixture", str(rejected.exception))

    def test_credential_rejection_never_displays_the_credential(self):
        credential = ("gh" + "p_" + "a" * 36).encode()
        with self.assertRaises(PublicationError) as rejected:
            check_public_files({"README.md": credential})
        self.assertNotIn(credential.decode(), str(rejected.exception))

    def test_source_and_public_documentation_are_accepted(self):
        result = check_public_files({"src/main/java/example/Demo.java": b"class Demo {}", "README.md": b"# BlockBlend", "ART_EXPERIMENTS.md": b"# Gallery", "RELEASE_NOTES.md": b"# Release"})
        self.assertEqual(result["file_count"], 4)

    def test_traversal_and_generated_dependencies_are_rejected(self):
        for path in ("../README.md", "src/main/java/../../secret.java", "docs/cache.jar", ".env", "src/main/resources/saves/level.json"):
            with self.subTest(path=path), self.assertRaises(PublicationError):
                check_public_files({path: b"fixture"})

    def test_unknown_binary_and_invalid_utf8_are_rejected(self):
        for data in (b"text\x00binary", b"\xff"):
            with self.subTest(data=data), self.assertRaises(PublicationError):
                check_public_files({"README.md": data})

    def test_spoofed_wrapper_is_rejected(self):
        with self.assertRaises(PublicationError):
            check_public_files({"gradle/wrapper/gradle-wrapper.jar": b"fake wrapper"})

    def test_private_machine_path_is_rejected(self):
        with self.assertRaises(PublicationError):
            check_public_files({"README.md": b"C:" + b"/Us" + b"ers/private/work"})

    def test_oversized_input_is_rejected_without_printing_contents(self):
        with self.assertRaises(PublicationError):
            check_public_files({"README.md": b"x" * (2 * 1024 * 1024 + 1)})

    def test_distributable_excludes_smoke_showcase_and_tests(self):
        for extra in ("com/example/blockblend/smoke/Smoke.class", "com/example/blockblend/showcase/Gallery.class", "org/junit/Test.class", "com/example/blockblend/DemoTest.class"):
            with self.subTest(extra=extra), self.assertRaises(PublicationError):
                check_mod_jar(self.jar(extra=extra), "0.3.1")

    @staticmethod
    def jar(extra=None, metadata=None):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, "w") as archive:
            archive.writestr("fabric.mod.json", metadata or json.dumps({"id": "blockblend", "version": "0.3.1", "entrypoints": {"main": ["com.example.blockblend.BlockBlendMod"], "client": ["com.example.blockblend.client.BlockBlendClient"]}}))
            archive.writestr("LICENSE", "MIT License")
            archive.writestr("com/example/blockblend/BlockBlendMod.class", b"fixture")
            if extra:
                archive.writestr(extra, b"fixture")
        return stream.getvalue()

    def test_valid_mod_archive_is_accepted(self):
        result = check_mod_jar(self.jar(), "0.3.1")
        self.assertEqual(result["version"], "0.3.1")

    def test_malformed_deep_and_duplicate_metadata_are_rejected(self):
        for metadata in ("{", "[" * 50 + "0" + "]" * 50, '{"id":"blockblend","id":"other"}', "x" * 20000):
            with self.subTest(metadata=metadata[:10]), self.assertRaises(PublicationError):
                check_mod_jar(self.jar(metadata=metadata), "0.3.1")

    def test_traversal_archive_entry_is_rejected(self):
        with self.assertRaises(PublicationError):
            check_mod_jar(self.jar(extra="../private.txt"), "0.3.1")

    @staticmethod
    def png(extra=None):
        def chunk(kind, payload):
            return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload))
        data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0))
        if extra:
            data += chunk(extra, b"private metadata fixture")
        return data + chunk(b"IDAT", zlib.compress(b"\x00\x00\x00\x00")) + chunk(b"IEND", b"")

    def test_anonymous_png_without_metadata_is_accepted(self):
        self.assertEqual(check_public_files({"docs/images/gallery.png": self.png()})["file_count"], 1)

    def test_private_image_metadata_and_trailing_data_are_rejected(self):
        for data in (self.png(b"tEXt"), self.png(b"eXIf"), self.png() + b"private data", self.png()[:-1]):
            with self.subTest(bytes=len(data)), self.assertRaises(PublicationError):
                check_public_files({"docs/images/gallery.png": data})


if __name__ == "__main__":
    unittest.main()
