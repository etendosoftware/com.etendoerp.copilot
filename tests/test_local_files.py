import base64
import os

from copilot.core.agent.agent_utils import process_local_files


def test_real_etendo_png():
    """Test with a real file etendo.png"""
    filename = "resources/images/etendo.png"
    assert os.path.isfile(filename), f"The file {filename} must exist in the current directory."
    images, others = process_local_files(filename)
    assert len(images) == 1
    assert others == []
    img_payload = images[0]
    assert img_payload["type"] == "image_url"
    assert img_payload["image_url"]["url"].startswith("data:image/png;base64,")

    # The base64 should match the real file
    with open(filename, "rb") as f:
        b64 = base64.b64encode(f.read()).decode("utf-8")
    expected_url = f"data:image/png;base64,{b64}"
    assert img_payload["image_url"]["url"] == expected_url
    assert img_payload["image_url"]["detail"] == "high"


def test_nonexistent_file(capsys):
    """Test for a file that does not exist"""
    fake_file = "no_existe_123.jpg"
    images, others = process_local_files(fake_file)
    assert images == []
    assert others == []
    captured = capsys.readouterr()
    assert f"Skipping: {fake_file} does not exist or is not a file" in captured.out


def test_not_an_image(tmp_path):
    """Test with an existing file that is not an image"""
    txt_file = tmp_path / "prueba.txt"
    txt_file.write_text("test text")
    images, others = process_local_files(str(txt_file))
    assert images == []
    assert others == [str(txt_file)]


def test_multiple_files(tmp_path):
    """Test multiple files, images and non-images, provided as a list"""
    img1 = tmp_path / "uno.jpg"
    img1.write_bytes(b"imagedata1")
    img2 = tmp_path / "dos.png"
    img2.write_bytes(b"imagedata2")
    txt = tmp_path / "readme.txt"
    txt.write_text("doc")
    files = [str(img1), str(img2), str(txt)]
    images, others = process_local_files(files)
    assert len(images) == 2
    urls = [i["image_url"]["url"] for i in images]
    for img, mime in [(img1, "image/jpeg"), (img2, "image/png")]:
        with open(img, "rb") as f:
            b64 = base64.b64encode(f.read()).decode()
            expected_url = f"data:{mime};base64,{b64}"
            assert expected_url in urls
    assert others == [str(txt)]


def test_comma_separated_string(tmp_path):
    """Test string separated by comma"""
    img = tmp_path / "tres.webp"
    img.write_bytes(b"imagedata3")
    pdf = tmp_path / "manual.pdf"
    pdf.write_bytes(b"%PDF-1.4")
    arg = f"{img}, {pdf}"
    images, others = process_local_files(arg)
    assert len(images) == 1
    assert others == [str(pdf)]


def test_list_with_comma_string(tmp_path):
    """Test list with a single comma-separated string"""
    img = tmp_path / "cuatro.gif"
    img.write_bytes(b"imagedata4")
    doc = tmp_path / "data.txt"
    doc.write_text("something")
    arg = [f"{img}, {doc}"]
    images, others = process_local_files(arg)
    assert len(images) == 1
    assert others == [str(doc)]


def test_empty_and_none():
    """Test with None and empty string as input"""
    assert process_local_files(None) == ([], [])
    assert process_local_files("") == ([], [])


def test_file_with_spaces(tmp_path):
    """Test files with spaces in the filename"""
    img = tmp_path / "cinco.jpg"
    img.write_bytes(b"imagedata5")
    txt = tmp_path / "  archivo con espacios.txt"
    txt.write_text("data")
    arg = f"{img} , {txt}"
    images, others = process_local_files(arg)
    assert len(images) == 1
    assert others == [str(txt)]


def test_file_extension_case(tmp_path):
    """Test file extension in upper/lower case"""
    img = tmp_path / "ejemplo.JPEG"
    img.write_bytes(b"imagedata6")
    images, others = process_local_files(str(img))
    assert len(images) == 1
    assert others == []


def test_directory_is_skipped(tmp_path, capsys):
    """Test passing a directory (should skip and print message)"""
    dirpath = tmp_path / "unadir"
    dirpath.mkdir()
    images, others = process_local_files(str(dirpath))
    assert images == []
    assert others == []
    captured = capsys.readouterr()
    assert f"Skipping: {dirpath} does not exist or is not a file" in captured.out
