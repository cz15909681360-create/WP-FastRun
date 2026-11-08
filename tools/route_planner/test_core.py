import unittest
from main import distance,parse_csv,csv_text,validate,project_data,atomic
import json
import tempfile
from pathlib import Path

class CoreTests(unittest.TestCase):
    def test_roundtrip(self):
        p=[[114.328,30.511],[114.329,30.512],[114.328,30.511]]
        self.assertEqual(parse_csv(csv_text(p)),p)
        self.assertGreater(distance(p),200)
    def test_invalid(self):
        for text in ['latitude,longitude\nNaN,114\n30,114','30,114\n30,114','"30",114\n31,115']:
            with self.assertRaises(ValueError):parse_csv(text)
    def test_limit(self):
        with self.assertRaises(ValueError):validate([[114,30]]*10001)
    def test_bom_comments(self):
        self.assertEqual(parse_csv('\ufefflatitude,longitude\n# comment\n30,114\n31,115'),[[114,30],[115,31]])
    def test_project(self):
        p={'version':1,'anchors':[[114,30],[115,31]],'points':[[114,30],[115,31]],'reference':[[114,30],[115,31]],'valid':True,'key':'must not persist'}
        result=project_data(p)
        self.assertNotIn('key',result)
        with tempfile.TemporaryDirectory(prefix='校园 路线 ') as d:
            path=Path(d)/'测试.route.json'; atomic(path,json.dumps(result));self.assertEqual(project_data(json.loads(path.read_text())),result)

if __name__=='__main__':unittest.main()
