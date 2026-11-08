import unittest
from routing import Graph,walkable
from main import distance

def graph(extra='',direction=''):
    return Graph(f'''<osm><node id="1" lon="114" lat="30"/><node id="2" lon="114.002" lat="30"/><node id="3" lon="114.002" lat="30.002"/><node id="4" lon="114" lat="30.002"/><way id="5"><nd ref="1"/><nd ref="2"/><nd ref="3"/><nd ref="4"/><nd ref="1"/><tag k="highway" v="footway"/>{direction}</way>{extra}</osm>'''.encode())

class RoutingTests(unittest.TestCase):
    def test_same_edge_snap_and_closed(self):
        result=graph().route([[114.0005,30.0001],[114.0015,30.0001]],True)
        self.assertEqual(result['points'][0],result['points'][-1])
        self.assertAlmostEqual(result['snapped'][0][1],30)
        self.assertGreater(result['meters'],100)
        self.assertLess(result['meters'],250)
    def test_open(self):
        result=graph().route([[114,30],[114.002,30.002]],False)
        self.assertNotEqual(result['points'][0],result['points'][-1])
        self.assertGreater(len(result['points']),2) # Must follow the corner, not a diagonal.
    def test_foot_oneway(self):
        route=graph(direction='<tag k="oneway:foot" v="yes"/>').route([[114.0015,30],[114.0005,30]],False)
        self.assertGreater(route['meters'],500)
    def test_no_private_or_motorway(self):
        self.assertFalse(walkable({'highway':'service','access':'private'}))
        self.assertFalse(walkable({'highway':'motorway'}))
        self.assertTrue(walkable({'highway':'service','access':'private','foot':'yes'}))
        self.assertFalse(walkable({'highway':'footway','foot':'no'}))
    def test_no_route(self):
        extra='<node id="10" lon="114.004" lat="30"/><node id="11" lon="114.005" lat="30"/><way id="12"><nd ref="10"/><nd ref="11"/><tag k="highway" v="footway"/></way>'
        with self.assertRaisesRegex(ValueError,'不连通'):graph(extra).route([[114,30],[114.005,30]],False)
    def test_far_anchor(self):
        with self.assertRaisesRegex(ValueError,'100 米'):graph().route([[114,30],[115,31]],False)

if __name__=='__main__':unittest.main()
