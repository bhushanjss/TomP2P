package net.tomp2p.p2p;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import net.tomp2p.futures.FutureGet;
import net.tomp2p.futures.FuturePut;
import net.tomp2p.futures.FutureRemove;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.Number640;
import net.tomp2p.storage.Data;
import net.tomp2p.storage.StorageLayer;
import net.tomp2p.storage.StorageLayer.ProtectionEnable;
import net.tomp2p.storage.StorageLayer.ProtectionMode;
import net.tomp2p.storage.StorageMemory;
import net.tomp2p.utils.Utils;

import org.junit.Assert;
import org.junit.Test;

public class TestSecurity {
    final private static Random rnd = new Random(42L);

    @Test
    public void testPublicKeyReceived() throws Exception {
        final Random rnd = new Random(43L);
        Peer master = null;
        Peer slave1 = null;
        KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA");
        KeyPair pair1 = gen.generateKeyPair();
        KeyPair pair2 = gen.generateKeyPair();
        // make master
        try {
            master = new PeerMaker(new Number160(rnd)).keyPair(pair1).ports(4001).makeAndListen();
            // make slave
            slave1 = new PeerMaker(new Number160(rnd)).keyPair(pair2).masterPeer(master).makeAndListen();
            final AtomicBoolean gotPK = new AtomicBoolean(false);
            // set storage to test PK
            slave1.getPeerBean().storage(new StorageLayer(new StorageMemory()) {
            	
            	@Override
            	public Enum<?> put(Number640 key, Data newData, PublicKey publicKey, boolean putIfAbsent,
            			boolean domainProtection) {
            		System.err.println("P is " + publicKey);
                    gotPK.set(publicKey != null);
                    System.err.println("PK is " + gotPK);
            		return super.put(key, newData, publicKey, putIfAbsent, domainProtection);
            	}
            });
            // perfect routing
            boolean peerInMap1 = master.getPeerBean().peerMap().peerFound(slave1.getPeerAddress(), null);
            boolean peerInMap2 = slave1.getPeerBean().peerMap().peerFound(master.getPeerAddress(), null);
            Assert.assertEquals(true, peerInMap1);
            Assert.assertEquals(true, peerInMap2);
            //
            Number160 locationKey = new Number160(50);

            RequestP2PConfiguration rc = new RequestP2PConfiguration(1, 1, 0);
            master.put(locationKey).setData(new Data(new byte[100000])).setRequestP2PConfiguration(rc).setSign()
                    .start().awaitUninterruptibly();
            // master.put(locationKey, new Data("test"),
            // cs1).awaitUninterruptibly();
            Assert.assertEquals(true, gotPK.get());
            // without PK, this test should fail.
            master.put(locationKey).setData(new Data("test1")).setRequestP2PConfiguration(rc).start()
                    .awaitUninterruptibly();
            Assert.assertEquals(false, gotPK.get());
        } finally {
            master.shutdown();
            slave1.shutdown();
        }
    }

    @Test
    public void testPublicKeyReceivedDomain() throws Exception {
        final Random rnd = new Random(43L);
        Peer master = null;
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA");
            KeyPair pair1 = gen.generateKeyPair();
            // make master
            master = new PeerMaker(new Number160(rnd)).keyPair(pair1).ports(4001).makeAndListen();
            // make slave
            final AtomicBoolean gotPK = new AtomicBoolean(false);
            // set storage to test PK
            master.getPeerBean().storage(new StorageLayer(new StorageMemory()) {
            	@Override
            	public Enum<?> put(Number640 key, Data newData, PublicKey publicKey, boolean putIfAbsent,
            			boolean domainProtection) {
                    gotPK.set(publicKey != null);
                    System.err.println("PK is " + gotPK);
                    return super.put(key, newData, publicKey, putIfAbsent, domainProtection);
                }
            });
            //
            Number160 locationKey = new Number160(50);
            RequestP2PConfiguration rc = new RequestP2PConfiguration(1, 1, 0);
            master.put(locationKey).setData(Number160.ONE, new Data(new byte[2000])).setRequestP2PConfiguration(rc)
                    .setDomainKey(Number160.ONE).setSign().start().awaitUninterruptibly();
            Assert.assertEquals(true, gotPK.get());
            // without PK
            master.put(locationKey).setData(Number160.ONE, new Data("test1")).setRequestP2PConfiguration(rc)
                    .setDomainKey(Number160.ONE).start().awaitUninterruptibly();
            Assert.assertEquals(false, gotPK.get());
        } catch (Throwable t) {
        	Assert.fail(t.getMessage());
        	t.printStackTrace();
        } finally {
            master.shutdown();
        }
    }

    @Test
    public void testProtection() throws Exception {
        final Random rnd = new Random(43L);
        Peer master = null;
        Peer slave1 = null;
        Peer slave2 = null;
        KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA");
        KeyPair pair1 = gen.generateKeyPair();
        KeyPair pair2 = gen.generateKeyPair();
        KeyPair pair3 = gen.generateKeyPair();
        System.err.println("PPK1 " + pair1.getPublic());
        System.err.println("PPK2 " + pair2.getPublic());
        System.err.println("PPK3 " + pair3.getPublic());
        try {
            master = new PeerMaker(new Number160(rnd)).keyPair(pair1).ports(4001).makeAndListen();
            master.getPeerBean()
                    .storage()
                    .setProtection(ProtectionEnable.ALL, ProtectionMode.MASTER_PUBLIC_KEY, ProtectionEnable.ALL,
                            ProtectionMode.MASTER_PUBLIC_KEY);
            slave1 = new PeerMaker(new Number160(rnd)).keyPair(pair2).masterPeer(master).makeAndListen();
            slave1.getPeerBean()
                    .storage()
                    .setProtection(ProtectionEnable.ALL, ProtectionMode.MASTER_PUBLIC_KEY, ProtectionEnable.ALL,
                            ProtectionMode.MASTER_PUBLIC_KEY);
            slave2 = new PeerMaker(new Number160(rnd)).keyPair(pair3).masterPeer(master).makeAndListen();
            slave2.getPeerBean()
                    .storage()
                    .setProtection(ProtectionEnable.ALL, ProtectionMode.MASTER_PUBLIC_KEY, ProtectionEnable.ALL,
                            ProtectionMode.MASTER_PUBLIC_KEY);
            // perfect routing
            master.getPeerBean().peerMap().peerFound(slave1.getPeerAddress(), null);
            master.getPeerBean().peerMap().peerFound(slave2.getPeerAddress(), null);
            //
            slave1.getPeerBean().peerMap().peerFound(master.getPeerAddress(), null);
            slave1.getPeerBean().peerMap().peerFound(slave2.getPeerAddress(), null);
            //
            slave2.getPeerBean().peerMap().peerFound(master.getPeerAddress(), null);
            slave2.getPeerBean().peerMap().peerFound(slave1.getPeerAddress(), null);
            Number160 locationKey = new Number160(50);
            FuturePut fdht1 = master.put(locationKey).setData(new Number160(10), new Data("test1"))
                    .setDomainKey(Utils.makeSHAHash(pair3.getPublic().getEncoded())).setProtectDomain().start();
            fdht1.awaitUninterruptibly();
            Assert.assertEquals(true, fdht1.isSuccess());
            // try to insert in same domain from different peer
            FuturePut fdht2 = slave1.put(locationKey).setData(new Number160(11), new Data("tes2"))
                    .setDomainKey(Utils.makeSHAHash(pair3.getPublic().getEncoded())).setProtectDomain().start();
            fdht2.awaitUninterruptibly();
            Assert.assertEquals(false, fdht2.isSuccess());
            // insert from same peer but with public key protection
            FuturePut fdht3 = slave2.put(locationKey).setData(new Number160(12), new Data("tes2"))
                    .setDomainKey(Utils.makeSHAHash(pair3.getPublic().getEncoded())).setProtectDomain().start();
            fdht3.awaitUninterruptibly();
            Assert.assertEquals(true, fdht3.isSuccess());
            //
            // get at least 3 results, because we want to test the domain
            // removel feature
            RequestP2PConfiguration rc = new RequestP2PConfiguration(3, 3, 3);
            FutureGet fdht4 = slave1.get(locationKey).setAll().setRequestP2PConfiguration(rc)
                    .setDomainKey(Utils.makeSHAHash(pair3.getPublic().getEncoded())).start();
            fdht4.awaitUninterruptibly();
            Assert.assertEquals(true, fdht4.isSuccess());
            Assert.assertEquals(2, fdht4.getDataMap().size());
        } finally {
            master.shutdown();
            slave1.shutdown();
            slave2.shutdown();
        }
    }

    @Test
    public void testProtectionWithRemove() throws Exception {
        final Random rnd = new Random(42L);
        Peer master = null;
        Peer slave1 = null;
        Peer slave2 = null;
        KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA");
        KeyPair pair1 = gen.generateKeyPair();
        KeyPair pair2 = gen.generateKeyPair();
        KeyPair pair3 = gen.generateKeyPair();
        System.err.println("PPK1 " + pair1.getPublic());
        System.err.println("PPK2 " + pair2.getPublic());
        System.err.println("PPK3 " + pair3.getPublic());
        try {
            master = new PeerMaker(new Number160(rnd)).keyPair(pair1).ports(4001).makeAndListen();
            master.getPeerBean()
                    .storage()
                    .setProtection(ProtectionEnable.ALL, ProtectionMode.MASTER_PUBLIC_KEY, ProtectionEnable.ALL,
                            ProtectionMode.MASTER_PUBLIC_KEY);
            slave1 = new PeerMaker(new Number160(rnd)).keyPair(pair2).masterPeer(master).makeAndListen();
            slave1.getPeerBean()
                    .storage()
                    .setProtection(ProtectionEnable.ALL, ProtectionMode.MASTER_PUBLIC_KEY, ProtectionEnable.ALL,
                            ProtectionMode.MASTER_PUBLIC_KEY);
            slave2 = new PeerMaker(new Number160(rnd)).keyPair(pair3).masterPeer(master).makeAndListen();
            slave2.getPeerBean()
                    .storage()
                    .setProtection(ProtectionEnable.ALL, ProtectionMode.MASTER_PUBLIC_KEY, ProtectionEnable.ALL,
                            ProtectionMode.MASTER_PUBLIC_KEY);
            // perfect routing
            master.getPeerBean().peerMap().peerFound(slave1.getPeerAddress(), null);
            master.getPeerBean().peerMap().peerFound(slave2.getPeerAddress(), null);
            //
            slave1.getPeerBean().peerMap().peerFound(master.getPeerAddress(), null);
            slave1.getPeerBean().peerMap().peerFound(slave2.getPeerAddress(), null);
            //
            slave2.getPeerBean().peerMap().peerFound(master.getPeerAddress(), null);
            slave2.getPeerBean().peerMap().peerFound(slave1.getPeerAddress(), null);
            Number160 locationKey = new Number160(50);
            FuturePut fdht1 = master.put(locationKey).setData(new Data("test1"))
                    .setDomainKey(Utils.makeSHAHash(pair1.getPublic().getEncoded())).setProtectDomain().start();
            fdht1.awaitUninterruptibly();
            // remove from different peer, should fail
            FutureRemove fdht2 = slave1.remove(locationKey)
                    .setDomainKey(Utils.makeSHAHash(pair1.getPublic().getEncoded())).setSign().start();
            fdht2.awaitUninterruptibly();
            Assert.assertFalse(fdht2.isSuccess());
            // this should work
            FutureRemove fdht3 = master.remove(locationKey)
                    .setDomainKey(Utils.makeSHAHash(pair1.getPublic().getEncoded())).setSign().start();
            fdht3.awaitUninterruptibly();
            Assert.assertTrue(fdht3.isSuccess());
        } finally {
            master.shutdown();
            slave1.shutdown();
            slave2.shutdown();
        }
    }

    @Test
    public void testProtectionDomain() throws Exception {
        final Random rnd = new Random(43L);
        Peer master = null;
        Peer slave1 = null;
        KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA");
        KeyPair pair1 = gen.generateKeyPair();
        KeyPair pair2 = gen.generateKeyPair();
        // make master
        try {
            master = new PeerMaker(new Number160(rnd)).keyPair(pair1).ports(4001).makeAndListen();
            // make slave
            slave1 = new PeerMaker(new Number160(rnd)).keyPair(pair2).masterPeer(master).makeAndListen();
            master.getPeerBean().storage(new StorageLayer(new StorageMemory()) {
            	@Override
            	public Enum<?> put(Number640 key, Data newData, PublicKey publicKey, boolean putIfAbsent,
            			boolean domainProtection) {
                    // System.out.println("store1");
            		return super.put(key, newData, publicKey, putIfAbsent, domainProtection);
                }
            });
            slave1.getPeerBean().storage(new StorageLayer(new StorageMemory()) {
            	@Override
            	public Enum<?> put(Number640 key, Data newData, PublicKey publicKey, boolean putIfAbsent,
            			boolean domainProtection) {
                    // System.out.println("store2");
            		return super.put(key, newData, publicKey, putIfAbsent, domainProtection);
                }
            });
            // perfect routing
            boolean peerInMap1 = master.getPeerBean().peerMap().peerFound(slave1.getPeerAddress(), null);
            boolean peerInMap2 = slave1.getPeerBean().peerMap().peerFound(master.getPeerAddress(), null);
            Assert.assertEquals(true, peerInMap1);
            Assert.assertEquals(true, peerInMap2);

            // since we have to peers, we store on both, otherwise this test may
            // sometimes work, sometimes not.
            RequestP2PConfiguration rc = new RequestP2PConfiguration(1, 1, 1);
            Number160 locationKey = Number160.createHash("loctaion");
            FuturePut futureDHT = master.put(locationKey).setData(Number160.createHash("content1"), new Data("test1"))
                    .setDomainKey(Number160.createHash("domain1")).setProtectDomain().setRequestP2PConfiguration(rc)
                    .start();
            futureDHT.awaitUninterruptibly();
            Assert.assertEquals(true, futureDHT.isSuccess());
            // now the slave stores with different in the same domain. This
            // should not work
            futureDHT = slave1.put(locationKey).setData(Number160.createHash("content2"), new Data("test2"))
                    .setDomainKey(Number160.createHash("domain1")).setProtectDomain().setRequestP2PConfiguration(rc)
                    .start();
            futureDHT.awaitUninterruptibly();
            System.err.println(futureDHT.getFailedReason());
            Assert.assertEquals(false, futureDHT.isSuccess());
        } finally {
            master.shutdown();
            slave1.shutdown();
        }
    }

    @Test
    public void testSecurePutGet1() throws Exception {
        Peer master = null;
        Peer slave1 = null;
        Peer slave2 = null;
        KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA");
        KeyPair pair1 = gen.generateKeyPair();
        KeyPair pair2 = gen.generateKeyPair();
        KeyPair pair3 = gen.generateKeyPair();
        System.err.println("PPK1 " + pair1.getPublic());
        System.err.println("PPK2 " + pair2.getPublic());
        System.err.println("PPK3 " + pair3.getPublic());
        try {

            // make slave
            master = new PeerMaker(new Number160(rnd)).keyPair(pair1).ports(4001).makeAndListen();
            master.getPeerBean()
                    .storage()
                    .setProtection(ProtectionEnable.ALL, ProtectionMode.MASTER_PUBLIC_KEY, ProtectionEnable.ALL,
                            ProtectionMode.MASTER_PUBLIC_KEY);
            slave1 = new PeerMaker(new Number160(rnd)).keyPair(pair2).masterPeer(master).makeAndListen();
            slave1.getPeerBean()
                    .storage()
                    .setProtection(ProtectionEnable.ALL, ProtectionMode.MASTER_PUBLIC_KEY, ProtectionEnable.ALL,
                            ProtectionMode.MASTER_PUBLIC_KEY);
            slave2 = new PeerMaker(new Number160(rnd)).keyPair(pair3).masterPeer(master).makeAndListen();
            slave2.getPeerBean()
                    .storage()
                    .setProtection(ProtectionEnable.ALL, ProtectionMode.MASTER_PUBLIC_KEY, ProtectionEnable.ALL,
                            ProtectionMode.MASTER_PUBLIC_KEY);
            // perfect routing
            master.getPeerBean().peerMap().peerFound(slave1.getPeerAddress(), null);
            master.getPeerBean().peerMap().peerFound(slave2.getPeerAddress(), null);
            //
            slave1.getPeerBean().peerMap().peerFound(master.getPeerAddress(), null);
            slave1.getPeerBean().peerMap().peerFound(slave2.getPeerAddress(), null);
            //
            slave2.getPeerBean().peerMap().peerFound(master.getPeerAddress(), null);
            slave2.getPeerBean().peerMap().peerFound(slave1.getPeerAddress(), null);
            Number160 locationKey = new Number160(50);

            Data data1 = new Data("test1");
            data1.setProtectedEntry();
            FuturePut fdht1 = master.put(locationKey).setData(data1).setSign().start();
            fdht1.awaitUninterruptibly();
            fdht1.getFutureRequests().awaitUninterruptibly();
            Assert.assertEquals(true, fdht1.isSuccess());
            // store again
            Data data2 = new Data("test1");
            data2.setProtectedEntry();
            FuturePut fdht2 = slave1.put(locationKey).setData(data2).setSign().start();
            fdht2.awaitUninterruptibly();
            fdht2.getFutureRequests().awaitUninterruptibly();
            Assert.assertEquals(0, fdht2.getResult().size());
            Assert.assertEquals(false, fdht2.isSuccess());
            // Utils.sleep(1000000);
            // try to removze it
            FutureRemove fdht3 = slave2.remove(locationKey).start();
            fdht3.awaitUninterruptibly();
            // true, since we have domain protection yet
            Assert.assertEquals(true, fdht3.isSuccess());
            Assert.assertEquals(0, fdht3.getEvalKeys().size());
            // try to put another thing
            Data data3 = new Data("test2");
            data3.setProtectedEntry();
            FuturePut fdht4 = master.put(locationKey).setSign().setData(new Number160(33), data3).start();
            fdht4.awaitUninterruptibly();
            fdht4.getFutureRequests().awaitUninterruptibly();
            Assert.assertEquals(true, fdht4.isSuccess());
            // get it
            FutureGet fdht7 = slave2.get(locationKey).setAll().start();
            fdht7.awaitUninterruptibly();
            Assert.assertEquals(2, fdht7.getDataMap().size());
            Assert.assertEquals(true, fdht7.isSuccess());
            // if(true)
            // System.exit(0);
            // try to remove for real, all
            FutureRemove fdht5 = master.remove(locationKey).setSign().setAll().setSign().start();
            fdht5.awaitUninterruptibly();
            System.err.println(fdht5.getFailedReason());
            Assert.assertEquals(true, fdht5.isSuccess());
            // get all, they should be removed now
            FutureGet fdht6 = slave2.get(locationKey).setAll().start();
            fdht6.awaitUninterruptibly();
            Assert.assertEquals(0, fdht6.getDataMap().size());
            Assert.assertEquals(false, fdht6.isSuccess());
            // put there the data again...
            FuturePut fdht8 = slave1.put(locationKey)
                    .setData(Utils.makeSHAHash(pair1.getPublic().getEncoded()), new Data("test1")).setSign().start();
            fdht8.awaitUninterruptibly();
            fdht8.getFutureRequests().awaitUninterruptibly();
            Assert.assertEquals(true, fdht8.isSuccess());
            // overwrite
            Data data4 = new Data("test1");
            data4.setProtectedEntry();
            FuturePut fdht9 = master.put(locationKey).setData(Utils.makeSHAHash(pair1.getPublic().getEncoded()), data4).setSign()
                    .start();
            fdht9.awaitUninterruptibly();
            fdht9.getFutureRequests().awaitUninterruptibly();
            System.err.println("reason " + fdht9.getFailedReason());
            Assert.assertEquals(true, fdht9.isSuccess());
        } finally {
            // Utils.sleep(1000000);
            master.shutdown();
            slave1.shutdown();
            slave2.shutdown();
        }
    }
    
    @Test
    public void testContentProtectoin() throws IOException, ClassNotFoundException, NoSuchAlgorithmException, InterruptedException, InvalidKeyException, SignatureException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA");

        KeyPair keyPairPeer1 = gen.generateKeyPair();
        Peer p1 = new PeerMaker(Number160.createHash(1)).setEnableIndirectReplication(false).ports(4838)
                .keyPair(keyPairPeer1).makeAndListen();
        KeyPair keyPairPeer2 = gen.generateKeyPair();
        Peer p2 = new PeerMaker(Number160.createHash(2)).setEnableIndirectReplication(false).masterPeer(p1)
                .keyPair(keyPairPeer2).makeAndListen();

        p2.bootstrap().setPeerAddress(p1.getPeerAddress()).start().awaitUninterruptibly();
        p1.bootstrap().setPeerAddress(p2.getPeerAddress()).start().awaitUninterruptibly();
        KeyPair keyPair = gen.generateKeyPair();

        String locationKey = "location";
        Number160 lKey = Number160.createHash(locationKey);
        String contentKey = "content";
        Number160 cKey = Number160.createHash(contentKey);

        String testData1 = "data1";
        Data data = new Data(testData1).setProtectedEntry();
        // put trough peer 1 with key pair -------------------------------------------------------
        FuturePut futurePut1 = p1.put(lKey).setData(cKey, data).keyPair(keyPair).start();
        futurePut1.awaitUninterruptibly();
        Assert.assertTrue(futurePut1.isSuccess());

        FutureGet futureGet1a = p1.get(lKey).setContentKey(cKey).start();
        futureGet1a.awaitUninterruptibly();

        Assert.assertTrue(futureGet1a.isSuccess());
        Assert.assertEquals(testData1, (String) futureGet1a.getData().object());
        FutureGet futureGet1b = p2.get(lKey).setContentKey(cKey).start();
        futureGet1b.awaitUninterruptibly();

        Assert.assertTrue(futureGet1b.isSuccess());
        Assert.assertEquals(testData1, (String) futureGet1b.getData().object());
        // put trough peer 2 without key pair ----------------------------------------------------
        String testData2 = "data2";
        Data data2 = new Data(testData2);
        FuturePut futurePut2 = p2.put(lKey).setData(cKey, data2).start();
        futurePut2.awaitUninterruptibly();

        /*
         * Shouldn't the future fail here? And why answers a peer here with a PutStatus.OK? Should be not here something
         * like PutStatus.FAILED_SECURITY?
         */
        
        Assert.assertFalse(futurePut2.isSuccess());
        
        FutureGet futureGet2 = p2.get(lKey).setContentKey(cKey).start();
        futureGet2.awaitUninterruptibly();
        Assert.assertTrue(futureGet2.isSuccess());
        // should have been not modified
        Assert.assertEquals(testData1, (String) futureGet2.getData().object());
        // put trough peer 1 without key pair ----------------------------------------------------
        String testData3 = "data3";
        Data data3 = new Data(testData3);
        FuturePut futurePut3 = p2.put(lKey).setData(cKey, data3).start();
        futurePut3.awaitUninterruptibly();

        /*
         * Shouldn't the future fail here? And why answers a peer here with PutStatus.OK from two peers? Should be not
         * here something like PutStatus.FAILED_SECURITY?
         */
       
        Assert.assertFalse(futurePut3.isSuccess());
        
        FutureGet futureGet3 = p2.get(lKey).setContentKey(cKey).start();
        futureGet3.awaitUninterruptibly();
        Assert.assertTrue(futureGet3.isSuccess());
        // should have been not modified ---> why it has been modified without giving a key pair?
        Assert.assertEquals(testData1, (String) futureGet3.getData().object());
        Assert.assertEquals(null, futureGet3.getData().publicKey());
        
        //now we store a signed data object and we will get back the public key as well
        data = new Data("Juhuu").setProtectedEntry().sign(keyPair);
        FuturePut futurePut4 = p1.put(lKey).setData(cKey, data).keyPair(keyPair).start();
        futurePut4.awaitUninterruptibly();
        Assert.assertTrue(futurePut4.isSuccess());
        FutureGet futureGet4 = p2.get(lKey).setContentKey(cKey).start();
        futureGet4.awaitUninterruptibly();
        Assert.assertTrue(futureGet4.isSuccess());
        // should have been not modified ---> why it has been modified without giving a key pair?
        Assert.assertEquals("Juhuu", (String) futureGet4.getData().object());
        Assert.assertEquals(keyPair.getPublic(), futureGet4.getData().publicKey());
        

        p1.shutdown().awaitUninterruptibly();
        p2.shutdown().awaitUninterruptibly();
    }
    
    @Test
    public void testContentProtectoinGeneric() throws IOException, ClassNotFoundException, NoSuchAlgorithmException, InterruptedException, InvalidKeyException, SignatureException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA");

        KeyPair keyPair = gen.generateKeyPair();
        Peer p1 = new PeerMaker(Number160.createHash(1)).setEnableIndirectReplication(false).ports(4838)
                .keyPair(keyPair).makeAndListen();
        
        String locationKey = "location";
        Number160 lKey = Number160.createHash(locationKey);
        String contentKey = "content";
        Number160 cKey = Number160.createHash(contentKey);
        
        StringBuilder sb = new StringBuilder();
        for(int i=0;i<1000;i++) {
        	sb.append(i);
        }
        
        Data data = new Data(sb.toString()).setProtectedEntry().sign(keyPair);
        FuturePut futurePut4 = p1.put(lKey).setData(cKey, data).keyPair(keyPair).start();
        futurePut4.awaitUninterruptibly();
        System.err.println(futurePut4.getFailedReason());
        Assert.assertTrue(futurePut4.isSuccess());
        FutureGet futureGet4 = p1.get(lKey).setContentKey(cKey).start();
        futureGet4.awaitUninterruptibly();
        Assert.assertTrue(futureGet4.isSuccess());
        // should have been not modified ---> why it has been modified without giving a key pair?
        Assert.assertEquals(sb.toString(), (String) futureGet4.getData().object());
        Assert.assertEquals(keyPair.getPublic(), futureGet4.getData().publicKey());
        
        
        p1.shutdown().awaitUninterruptibly();
    }
    
    @Test
    public void testVersionContentProtectoinGeneric() throws IOException, ClassNotFoundException, NoSuchAlgorithmException, InterruptedException, InvalidKeyException, SignatureException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA");

        KeyPair keyPair1 = gen.generateKeyPair();
        KeyPair keyPair2 = gen.generateKeyPair();
        Peer p1 = new PeerMaker(Number160.createHash(1)).setEnableIndirectReplication(false).ports(4838)
                .keyPair(keyPair1).makeAndListen();
        Peer p2 = new PeerMaker(Number160.createHash(2)).setEnableIndirectReplication(false).ports(4839)
                .keyPair(keyPair2).makeAndListen();
        
        p2.bootstrap().setPeerAddress(p1.getPeerAddress()).start().awaitUninterruptibly();
        p1.bootstrap().setPeerAddress(p2.getPeerAddress()).start().awaitUninterruptibly();
        
        String locationKey = "location";
        Number160 lKey = Number160.createHash(locationKey);
        String contentKey = "content";
        Number160 cKey = Number160.createHash(contentKey);
        
        StringBuilder sb = new StringBuilder();
        for(int i=0;i<1000;i++) {
        	sb.append(i);
        }
        
        Data data1 = new Data(sb.toString()).setProtectedEntry().sign(keyPair1);
        
        FuturePut futurePut4 = p1.put(lKey).setData(cKey, data1).keyPair(keyPair1).setVersionKey(Number160.ZERO).start();
        futurePut4.awaitUninterruptibly();
        Assert.assertTrue(futurePut4.isSuccess());
        
        Data data2 = new Data(sb.toString()).setProtectedEntry().sign(keyPair2);
        
        FuturePut futurePut5 = p2.put(lKey).setData(cKey, data2).keyPair(keyPair2).setVersionKey(Number160.ONE).start();
        futurePut5.awaitUninterruptibly();
        Assert.assertTrue(!futurePut5.isSuccess());
        
        Data data3 = new Data(sb.toString()).setProtectedEntry().sign(keyPair2);
        FuturePut futurePut6 = p1.put(lKey).setData(cKey, data3).keyPair(keyPair1).setVersionKey(Number160.MAX_VALUE).start();
        futurePut6.awaitUninterruptibly();
        Assert.assertTrue(futurePut6.isSuccess());
        
        FuturePut futurePut7 = p2.put(lKey).setData(cKey, data2).keyPair(keyPair2).setVersionKey(Number160.ONE).start();
        futurePut7.awaitUninterruptibly();
        Assert.assertTrue(futurePut7.isSuccess());
        
        
        p1.shutdown().awaitUninterruptibly();
        p2.shutdown().awaitUninterruptibly();
    }
    
    @Test
    public void testChangeDomainProtectionKey() throws IOException, ClassNotFoundException, NoSuchAlgorithmException, InterruptedException, InvalidKeyException, SignatureException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA");

        KeyPair keyPair1 = gen.generateKeyPair();
        KeyPair keyPair2 = gen.generateKeyPair();
        Peer p1 = new PeerMaker(Number160.createHash(1)).setEnableIndirectReplication(false).ports(4838)
                .keyPair(keyPair1).makeAndListen();
        Peer p2 = new PeerMaker(Number160.createHash(2)).setEnableIndirectReplication(false).ports(4839)
                .keyPair(keyPair2).makeAndListen();
        
        p2.bootstrap().setPeerAddress(p1.getPeerAddress()).start().awaitUninterruptibly();
        p1.bootstrap().setPeerAddress(p2.getPeerAddress()).start().awaitUninterruptibly();
        
        Data data = new Data("test");
        FuturePut fp1 = p1.put(Number160.createHash("key1")).setProtectDomain().setData(data).start().awaitUninterruptibly();
        Assert.assertTrue(fp1.isSuccess());
        FuturePut fp2 = p2.put(Number160.createHash("key1")).setProtectDomain().setData(data).start().awaitUninterruptibly();
        Assert.assertTrue(!fp2.isSuccess());
        
        FuturePut fp3 = p1.put(Number160.createHash("key1")).changePublicKey(keyPair2.getPublic()).start().awaitUninterruptibly();
        Assert.assertTrue(fp3.isSuccess());
        FuturePut fp4 = p2.put(Number160.createHash("key1")).setProtectDomain().setData(data).start().awaitUninterruptibly();
        Assert.assertTrue(fp4.isSuccess());
        
        p1.shutdown().awaitUninterruptibly();
        p2.shutdown().awaitUninterruptibly();
    }
    
    @Test
    public void testChangeEntryProtectionKey() throws IOException, ClassNotFoundException, NoSuchAlgorithmException, InterruptedException, InvalidKeyException, SignatureException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA");

        KeyPair keyPair1 = gen.generateKeyPair();
        KeyPair keyPair2 = gen.generateKeyPair();
        Peer p1 = new PeerMaker(Number160.createHash(1)).setEnableIndirectReplication(false).ports(4838)
                .keyPair(keyPair1).makeAndListen();
        Peer p2 = new PeerMaker(Number160.createHash(2)).setEnableIndirectReplication(false).ports(4839)
                .keyPair(keyPair2).makeAndListen();
        
        p2.bootstrap().setPeerAddress(p1.getPeerAddress()).start().awaitUninterruptibly();
        p1.bootstrap().setPeerAddress(p2.getPeerAddress()).start().awaitUninterruptibly();
        
        Data data = new Data("test").setProtectedEntry();
        FuturePut fp1 = p1.put(Number160.createHash("key1")).setSign().setData(data).start().awaitUninterruptibly();
        Assert.assertTrue(fp1.isSuccess());
        FuturePut fp2 = p2.put(Number160.createHash("key1")).setData(data).start().awaitUninterruptibly();
        Assert.assertTrue(!fp2.isSuccess());
        
        Data data2 = new Data().setProtectedEntry();
        data2.publicKey(keyPair2.getPublic());
        FuturePut fp3 = p1.put(Number160.createHash("key1")).setSign().putMeta().setData(data2).start().awaitUninterruptibly();
        Assert.assertTrue(fp3.isSuccess());
        
        FuturePut fp4 = p2.put(Number160.createHash("key1")).setSign().setData(data).start().awaitUninterruptibly();
        Assert.assertTrue(fp4.isSuccess());   
        
        p1.shutdown().awaitUninterruptibly();
        p2.shutdown().awaitUninterruptibly();
    }
    
    @Test
    public void testChangeEntryProtectionKeySignature() throws IOException, ClassNotFoundException, NoSuchAlgorithmException, InterruptedException, InvalidKeyException, SignatureException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA");

        KeyPair keyPair1 = gen.generateKeyPair();
        KeyPair keyPair2 = gen.generateKeyPair();
        Peer p1 = new PeerMaker(Number160.createHash(1)).setEnableIndirectReplication(false).ports(4838)
                .keyPair(keyPair1).makeAndListen();
        Peer p2 = new PeerMaker(Number160.createHash(2)).setEnableIndirectReplication(false).ports(4839)
                .keyPair(keyPair2).makeAndListen();
        
        p2.bootstrap().setPeerAddress(p1.getPeerAddress()).start().awaitUninterruptibly();
        p1.bootstrap().setPeerAddress(p2.getPeerAddress()).start().awaitUninterruptibly();
        
        Data data = new Data("test1").setProtectedEntry().sign(keyPair1);
        FuturePut fp1 = p1.put(Number160.createHash("key1")).setSign().setData(data).start().awaitUninterruptibly();
        Assert.assertTrue(fp1.isSuccess());
        FuturePut fp2 = p2.put(Number160.createHash("key1")).setData(data).start().awaitUninterruptibly();
        Assert.assertTrue(!fp2.isSuccess());
        
        Data data2 = new Data("test1").setProtectedEntry().sign(keyPair2).duplicateMeta();
        FuturePut fp3 = p1.put(Number160.createHash("key1")).setSign().putMeta().setData(data2).start().awaitUninterruptibly();
        Assert.assertTrue(fp3.isSuccess());
        
        Data retData = p2.get(Number160.createHash("key1")).start().awaitUninterruptibly().getData();
        Assert.assertTrue(retData.verify(keyPair2.getPublic()));
        
        p1.shutdown().awaitUninterruptibly();
        p2.shutdown().awaitUninterruptibly();
    }
    
    @Test
    public void testTTLUpdate() throws IOException, ClassNotFoundException, NoSuchAlgorithmException, InterruptedException, InvalidKeyException, SignatureException {
       
        Peer p1 = new PeerMaker(Number160.createHash(1)).setEnableIndirectReplication(false).storageIntervalMillis(1).ports(4838).makeAndListen();
        Peer p2 = new PeerMaker(Number160.createHash(2)).setEnableIndirectReplication(false).storageIntervalMillis(1).ports(4839).makeAndListen();
        
        p2.bootstrap().setPeerAddress(p1.getPeerAddress()).start().awaitUninterruptibly();
        p1.bootstrap().setPeerAddress(p2.getPeerAddress()).start().awaitUninterruptibly();
        
        Data data = new Data("test1");
        data.ttlSeconds(1);
        FuturePut fp1 = p1.put(Number160.createHash("key1")).setData(data).start().awaitUninterruptibly();
        Assert.assertTrue(fp1.isSuccess());
        
        Thread.sleep(2000);
        Data retData = p2.get(Number160.createHash("key1")).start().awaitUninterruptibly().getData();
        Assert.assertNull(retData);
        
        FuturePut fp2 = p1.put(Number160.createHash("key1")).setData(data).start().awaitUninterruptibly();
        Assert.assertTrue(fp2.isSuccess());
        
        Data update = data.duplicateMeta();
        update.ttlSeconds(10);
        
        FuturePut fp3 = p1.put(Number160.createHash("key1")).putMeta().setData(update).start().awaitUninterruptibly();
        Assert.assertTrue(fp3.isSuccess());
        
        Thread.sleep(1000);
        retData = p2.get(Number160.createHash("key1")).start().awaitUninterruptibly().getData();
        Assert.assertEquals("test1", retData.object());
        
        p1.shutdown().awaitUninterruptibly();
        p2.shutdown().awaitUninterruptibly();
    }
    
    
	@Test
	public void testRemoveFromTo2() throws NoSuchAlgorithmException, IOException, InvalidKeyException,
	        SignatureException, ClassNotFoundException {
		KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA");

		KeyPair keyPairPeer1 = gen.generateKeyPair();
		Peer p1 = new PeerMaker(Number160.createHash(1)).ports(4838).keyPair(keyPairPeer1)
		        .setEnableIndirectReplication(true).makeAndListen();
		KeyPair keyPairPeer2 = gen.generateKeyPair();
		Peer p2 = new PeerMaker(Number160.createHash(2)).masterPeer(p1).keyPair(keyPairPeer2)
		        .setEnableIndirectReplication(true).makeAndListen();

		p2.bootstrap().setPeerAddress(p1.getPeerAddress()).start().awaitUninterruptibly();
		p1.bootstrap().setPeerAddress(p2.getPeerAddress()).start().awaitUninterruptibly();

		KeyPair key1 = gen.generateKeyPair();

		String locationKey = "location";
		Number160 lKey = Number160.createHash(locationKey);
		String domainKey = "domain";
		Number160 dKey = Number160.createHash(domainKey);
		String contentKey = "content";
		Number160 cKey = Number160.createHash(contentKey);

		String testData1 = "data1";
		Data data = new Data(testData1).setProtectedEntry().sign(key1);

		// put trough peer 1 with key pair
		// -------------------------------------------------------

		FuturePut futurePut1 = p1.put(lKey).setDomainKey(dKey).setData(cKey, data).keyPair(key1).start();
		futurePut1.awaitUninterruptibly();
		Assert.assertTrue(futurePut1.isSuccess());

		FutureGet futureGet1a = p1.get(lKey).setDomainKey(dKey).setContentKey(cKey).start();
		futureGet1a.awaitUninterruptibly();
		Assert.assertTrue(futureGet1a.isSuccess());
		Assert.assertEquals(testData1, (String) futureGet1a.getData().object());

		FutureGet futureGet1b = p2.get(lKey).setDomainKey(dKey).setContentKey(cKey).start();
		futureGet1b.awaitUninterruptibly();
		Assert.assertTrue(futureGet1b.isSuccess());
		Assert.assertEquals(testData1, (String) futureGet1b.getData().object());

		// remove with correct key pair
		// -----------------------------------------------------------

		FutureRemove futureRemove4 = p1.remove(lKey).from(new Number640(lKey, dKey, cKey, Number160.ZERO))
		        .to(new Number640(lKey, dKey, cKey, Number160.MAX_VALUE)).keyPair(key1).start();
		futureRemove4.awaitUninterruptibly();
		Assert.assertTrue(futureRemove4.isSuccess());

		FutureGet futureGet4a = p2.get(lKey).setDomainKey(dKey).setContentKey(cKey).start();
		futureGet4a.awaitUninterruptibly();
		// we did not find the data
		Assert.assertTrue(futureGet4a.isFailed());
		// should have been removed
		Assert.assertNull(futureGet4a.getData());

		FutureGet futureGet4b = p2.get(lKey).setContentKey(cKey).start();
		futureGet4b.awaitUninterruptibly();
		// we did not find the data
		Assert.assertTrue(futureGet4b.isFailed());
		// should have been removed
		Assert.assertNull(futureGet4b.getData());

		p1.shutdown().awaitUninterruptibly();
		p2.shutdown().awaitUninterruptibly();
	}
	
	@Test
	public void testRemoveFutureResponse() throws NoSuchAlgorithmException, IOException, InvalidKeyException,
	        SignatureException, ClassNotFoundException {
		KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA");

		KeyPair keyPairPeer1 = gen.generateKeyPair();
		Peer p1 = new PeerMaker(Number160.createHash(1)).ports(4838).keyPair(keyPairPeer1)
		        .setEnableIndirectReplication(true).makeAndListen();
		KeyPair keyPairPeer2 = gen.generateKeyPair();
		Peer p2 = new PeerMaker(Number160.createHash(2)).masterPeer(p1).keyPair(keyPairPeer2)
		        .setEnableIndirectReplication(true).makeAndListen();

		p2.bootstrap().setPeerAddress(p1.getPeerAddress()).start().awaitUninterruptibly();
		p1.bootstrap().setPeerAddress(p2.getPeerAddress()).start().awaitUninterruptibly();

		KeyPair key1 = gen.generateKeyPair();

		String locationKey = "location";
		Number160 lKey = Number160.createHash(locationKey);
		//String domainKey = "domain";
		//Number160 dKey = Number160.createHash(domainKey);
		String contentKey = "content";
		Number160 cKey = Number160.createHash(contentKey);

		String testData1 = "data1";
		Data data = new Data(testData1).setProtectedEntry().sign(key1);
		// put trough peer 1 with key pair
		// -------------------------------------------------------

		FuturePut futurePut1 = p1.put(lKey).setData(cKey, data).keyPair(key1).start();
		futurePut1.awaitUninterruptibly();
		Assert.assertTrue(futurePut1.isSuccess());

		FutureGet futureGet1a = p1.get(lKey).setContentKey(cKey).start();
		futureGet1a.awaitUninterruptibly();
		Assert.assertTrue(futureGet1a.isSuccess());
		Assert.assertEquals(testData1, (String) futureGet1a.getData().object());

		FutureGet futureGet1b = p2.get(lKey).setContentKey(cKey).start();
		futureGet1b.awaitUninterruptibly();
		Assert.assertTrue(futureGet1b.isSuccess());
		Assert.assertEquals(testData1, (String) futureGet1b.getData().object());

		// try to remove without key pair using the direct remove
		// -------------------------------
		// should fail

		FutureRemove futureRemoveDirect = p1.remove(lKey).contentKey(cKey).start();
		futureRemoveDirect.awaitUninterruptibly();
		// try to remove without key pair using the from/to
		// -------------------------------------
		// should fail
		FutureRemove futureRemoveFromTo = p1.remove(lKey).from(new Number640(lKey, Number160.ZERO, cKey, Number160.ZERO))
		        .to(new Number640(lKey, Number160.ZERO, cKey, Number160.MAX_VALUE)).start();
		futureRemoveFromTo.awaitUninterruptibly();

		Assert.assertEquals(futureRemoveDirect.isSuccess(), futureRemoveFromTo.isSuccess());
		Assert.assertFalse(futureRemoveDirect.isSuccess());
		Assert.assertFalse(futureRemoveFromTo.isSuccess());

		p1.shutdown().awaitUninterruptibly();
		p2.shutdown().awaitUninterruptibly();
	}
	
	
	@Test
	public void testChangeProtectionKey() throws NoSuchAlgorithmException, IOException, InvalidKeyException,
	        SignatureException, ClassNotFoundException {
		KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA");
		KeyPair keyPairPeer1 = gen.generateKeyPair();
		Peer p1 = new PeerMaker(Number160.createHash(1)).ports(4834).keyPair(keyPairPeer1)
		        .setEnableIndirectReplication(true).makeAndListen();
		KeyPair keyPairPeer2 = gen.generateKeyPair();
		Peer p2 = new PeerMaker(Number160.createHash(2)).masterPeer(p1).keyPair(keyPairPeer2)
		        .setEnableIndirectReplication(true).makeAndListen();
		p2.bootstrap().setPeerAddress(p1.getPeerAddress()).start().awaitUninterruptibly();
		p1.bootstrap().setPeerAddress(p2.getPeerAddress()).start().awaitUninterruptibly();
		KeyPair keyPair1 = gen.generateKeyPair();
		KeyPair keyPair2 = gen.generateKeyPair();
		Number160 lKey = Number160.createHash("location");
		Number160 dKey = Number160.createHash("domain");
		Number160 cKey = Number160.createHash("content");
		// initial put
		String testData = "data";
		Data data = new Data(testData).setProtectedEntry().sign(keyPair1);
		FuturePut futurePut1 = p1.put(lKey).setDomainKey(dKey).setSign().setData(cKey, data).keyPair(keyPair1).start();
		futurePut1.awaitUninterruptibly();
		Assert.assertTrue(futurePut1.isSuccess());
		Data retData = p1.get(lKey).setDomainKey(dKey).setContentKey(cKey).start().awaitUninterruptibly().getData();
		Assert.assertEquals(testData, (String) retData.object());
		Assert.assertTrue(retData.verify(keyPair1.getPublic()));
		retData = p2.get(lKey).setDomainKey(dKey).setContentKey(cKey).start().awaitUninterruptibly().getData();
		Assert.assertEquals(testData, (String) retData.object());
		Assert.assertTrue(retData.verify(keyPair1.getPublic()));
		// change the key pair to the new one using an empty data object
		data = new Data(testData).setProtectedEntry().sign(keyPair2).duplicateMeta();
		// use the old protection key to sign the message
		FuturePut futurePut2 = p1.put(lKey).setDomainKey(dKey).setSign().putMeta().setData(cKey, data)
		        .keyPair(keyPair1).start();
		futurePut2.awaitUninterruptibly();
		Assert.assertTrue(futurePut2.isSuccess());
		retData = p1.get(lKey).setDomainKey(dKey).setContentKey(cKey).start().awaitUninterruptibly().getData();
		Assert.assertEquals(testData, (String) retData.object());
		Assert.assertTrue(retData.verify(keyPair2.getPublic()));
		retData = p2.get(lKey).setDomainKey(dKey).setContentKey(cKey).start().awaitUninterruptibly().getData();
		Assert.assertEquals(testData, (String) retData.object());
		Assert.assertTrue(retData.verify(keyPair2.getPublic()));
		// should be not possible to modify
		data = new Data().setProtectedEntry().sign(keyPair1);
		FuturePut futurePut3 = p1.put(lKey).setDomainKey(dKey).setSign().setData(cKey, data).keyPair(keyPair1).start();
		futurePut3.awaitUninterruptibly();
		Assert.assertFalse(futurePut3.isSuccess());
		retData = p1.get(lKey).setDomainKey(dKey).setContentKey(cKey).start().awaitUninterruptibly().getData();
		Assert.assertEquals(testData, (String) retData.object());
		Assert.assertTrue(retData.verify(keyPair2.getPublic()));
		retData = p2.get(lKey).setDomainKey(dKey).setContentKey(cKey).start().awaitUninterruptibly().getData();
		Assert.assertEquals(testData, (String) retData.object());
		Assert.assertTrue(retData.verify(keyPair2.getPublic()));
		// modify with new protection key
		String newTestData = "new data";
		data = new Data(newTestData).setProtectedEntry().sign(keyPair2);
		FuturePut futurePut4 = p1.put(lKey).setDomainKey(dKey).setSign().setData(cKey, data).keyPair(keyPair2).start();
		futurePut4.awaitUninterruptibly();
		Assert.assertTrue(futurePut4.isSuccess());
		retData = p1.get(lKey).setDomainKey(dKey).setContentKey(cKey).start().awaitUninterruptibly().getData();
		Assert.assertEquals(newTestData, (String) retData.object());
		Assert.assertTrue(retData.verify(keyPair2.getPublic()));
		retData = p2.get(lKey).setDomainKey(dKey).setContentKey(cKey).start().awaitUninterruptibly().getData();
		Assert.assertEquals(newTestData, (String) retData.object());
		Assert.assertTrue(retData.verify(keyPair2.getPublic()));
		p1.shutdown().awaitUninterruptibly();
		p2.shutdown().awaitUninterruptibly();
	}

	@Test
	public void testChangeProtectionKeyWithVersions() throws NoSuchAlgorithmException, IOException,
	        ClassNotFoundException, InvalidKeyException, SignatureException {
		KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA");
		KeyPair keyPairPeer1 = gen.generateKeyPair();
		Peer p1 = new PeerMaker(Number160.createHash(1)).ports(4834).keyPair(keyPairPeer1)
		        .setEnableIndirectReplication(true).makeAndListen();
		KeyPair keyPairPeer2 = gen.generateKeyPair();
		Peer p2 = new PeerMaker(Number160.createHash(2)).masterPeer(p1).keyPair(keyPairPeer2)
		        .setEnableIndirectReplication(true).makeAndListen();
		p2.bootstrap().setPeerAddress(p1.getPeerAddress()).start().awaitUninterruptibly();
		p1.bootstrap().setPeerAddress(p2.getPeerAddress()).start().awaitUninterruptibly();
		KeyPair keyPair1 = gen.generateKeyPair();
		KeyPair keyPair2 = gen.generateKeyPair();
		Number160 lKey = Number160.createHash("location");
		Number160 dKey = Number160.createHash("domain");
		Number160 cKey = Number160.createHash("content");
		// put the first version of the content with key pair 1
		Number160 vKey1 = Number160.createHash("version1");
		Data data = new Data("data1v1").setProtectedEntry().sign(keyPair1);
		data.basedOn(Number160.ZERO);
		FuturePut futurePut1 = p1.put(lKey).setDomainKey(dKey).setSign().setData(cKey, data).keyPair(keyPair1)
		        .setVersionKey(vKey1).start();
		futurePut1.awaitUninterruptibly();
		Assert.assertTrue(futurePut1.isSuccess());
		// add another version with the correct key pair 1
		Number160 vKey2 = Number160.createHash("version2");
		data = new Data("data1v2").setProtectedEntry().sign(keyPair1);
		data.basedOn(vKey1);
		FuturePut futurePut2 = p1.put(lKey).setDomainKey(dKey).setSign().setData(cKey, data).keyPair(keyPair1)
		        .setVersionKey(vKey2).start();
		futurePut2.awaitUninterruptibly();
		Assert.assertTrue(futurePut2.isSuccess());
		// put new version with other key pair 2 (expected to fail)
		Number160 vKey3 = Number160.createHash("version3");
		data = new Data("data1v3").setProtectedEntry().sign(keyPair2);
		data.basedOn(vKey2);
		FuturePut futurePut3 = p1.put(lKey).setDomainKey(dKey).setData(cKey, data).keyPair(keyPair2)
		        .setVersionKey(vKey3).start();
		futurePut3.awaitUninterruptibly();
		Assert.assertFalse(futurePut3.isSuccess());
		// change the key pair to the new one using an empty data object
		data = new Data().setProtectedEntry().sign(keyPair2).duplicateMeta();
		// use the old protection key to sign the message
		FuturePut futurePut4 = p1.put(lKey).setDomainKey(dKey).setSign().putMeta().setData(cKey, data)
		        .keyPair(keyPair1).start();
		futurePut4.awaitUninterruptibly();
		Assert.assertFalse(futurePut4.isSuccess());
		// verify if the two versions have the new protection key
		Data retData = p1.get(lKey).setDomainKey(dKey).setContentKey(cKey).setVersionKey(vKey1).start()
		        .awaitUninterruptibly().getData();
		Assert.assertEquals("data1v1", (String) retData.object());
		Assert.assertFalse(retData.verify(keyPair2.getPublic()));
		retData = p1.get(lKey).setDomainKey(dKey).setContentKey(cKey).setVersionKey(vKey2).start()
		        .awaitUninterruptibly().getData();
		//Assert.assertEquals("data1v2", (String) retData.object());
		Assert.assertFalse(retData.verify(keyPair2.getPublic()));
		// add another version with the new protection key
		Number160 vKey4 = Number160.createHash("version4");
		data = new Data("data1v4").setProtectedEntry().sign(keyPair2);
		data.basedOn(vKey2);
		FuturePut futurePut5 = p1.put(lKey).setDomainKey(dKey).setSign().setData(cKey, data).keyPair(keyPair2)
		        .setVersionKey(vKey4).start();
		futurePut5.awaitUninterruptibly();
		Assert.assertTrue(futurePut5.isSuccess());
		p1.shutdown().awaitUninterruptibly();
		p2.shutdown().awaitUninterruptibly();
	}
}
