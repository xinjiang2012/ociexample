package net.xin2012.oci;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimplePrivateKeySupplier;
import com.oracle.bmc.core.Blockstorage;
import com.oracle.bmc.core.BlockstorageClient;
import com.oracle.bmc.core.BlockstorageWaiters;
import com.oracle.bmc.core.model.CreateVolumeDetails;
import com.oracle.bmc.core.model.UpdateVolumeDetails;
import com.oracle.bmc.core.model.Volume;
import com.oracle.bmc.core.requests.*;
import com.oracle.bmc.core.responses.CreateVolumeResponse;
import com.oracle.bmc.core.responses.GetVolumeResponse;
import com.oracle.bmc.core.responses.ListVolumesResponse;
import com.oracle.bmc.identity.Identity;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.requests.ListAvailabilityDomainsRequest;
import com.oracle.bmc.model.BmcException;

public class BlockstorageExample {

    private Blockstorage blockstorage;

    public void setBlockstorage(Blockstorage blockstorage) {
        this.blockstorage = blockstorage;
    }

    public Blockstorage getBlockstorage() {
        return this.blockstorage;
    }

    public GetVolumeResponse createVolume(String displayName, String availabilityDomain, String compartmentId, Long size) {
        try {
            //Create Volume
            CreateVolumeResponse createVolumeResponse =
                    blockstorage.createVolume(
                            CreateVolumeRequest.builder()
                                    .createVolumeDetails(
                                            CreateVolumeDetails.builder()
                                                    .displayName(displayName)
                                                    .availabilityDomain(availabilityDomain)
                                                    .compartmentId(compartmentId)
                                                    .sizeInGBs(size)
                                                    .build())
                                    .build());
            Volume volume = createVolumeResponse.getVolume();
            //Waite for volume creation complete
            return waitForVolumeActionToComplete(blockstorage, volume.getId(), Volume.LifecycleState.Available);
        } catch (BmcException e) {
            System.out.println("Error during blockstorage creation, detailed message is: \n " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Failed while waiting block volume creation in creation stage, detailed message is : \n " + e.getMessage());
        }
        return null;
    }


    public void deleteVolume(String volumeId) throws Exception {
        blockstorage.deleteVolume(
                DeleteVolumeRequest.builder().volumeId(volumeId).build());
        try {
            waitForVolumeActionToComplete(blockstorage, volumeId, Volume.LifecycleState.Terminated);
        } catch (Exception e) {
            System.out.println("Error while waiting block volume creation in terminated stage, detailed message is: \n " + e.getMessage());
            throw e;
        }
    }

    public GetVolumeResponse updateVolume(String volumeId, Long updatedVolumeSize) throws Exception {
        try {
            blockstorage.updateVolume(UpdateVolumeRequest.builder()
                    .volumeId(volumeId)
                    .updateVolumeDetails(UpdateVolumeDetails.builder()
                            .sizeInGBs(updatedVolumeSize)
                            .build())
                    .build());
            return waitForVolumeActionToComplete(blockstorage, volumeId, Volume.LifecycleState.Available);
        } catch (BmcException e) {
            System.out.println("Error during blockstorage update, detailed message is: \n " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.out.println("Error while waiting blockstorage in available stage, detailed message is: \n " + e.getMessage());
            throw e;
        }
    }

    public ListVolumesResponse listVolumes(String compartmentId) {
        try {
            return blockstorage.listVolumes(ListVolumesRequest.builder().compartmentId(compartmentId).build());
        } catch (BmcException e) {
            System.out.println("Error during blockstorage list request, detailed message is: \n " + e.getMessage());
            throw e;
        }
    }

    private GetVolumeResponse waitForVolumeActionToComplete(
            Blockstorage blockstorage,
            String volumeId,
            Volume.LifecycleState targetLifecycleState) throws Exception {

        BlockstorageWaiters waiter = blockstorage.getWaiters();
        return waiter.forVolume(
                GetVolumeRequest.builder().volumeId(volumeId).build(),
                targetLifecycleState)
                .execute();
    }

    public String getAvailableDomain(Identity identity, String compartmentId) {
        String availabilityDomain =
                identity
                        .listAvailabilityDomains(
                                ListAvailabilityDomainsRequest.builder().compartmentId(compartmentId).build())
                        .getItems()
                        .stream()
                        .map(ad -> ad.getName())
                        .findAny()
                        .orElseThrow(() -> new RuntimeException("AD List cannot be empty"));
        return availabilityDomain;
    }


    public static void main(String[] args) {
        String tenancyOcid = "ocid1.tenancy.oc1..aaaaaaaadkoovy5roxsjqrvbw7eykpgsfm2tb6yxvbcvezsvugac6nnsifiq";
        String userFingerprint = "09:e4:86:07:74:59:8a:94:5f:7f:06:4e:cc:ae:9e:16";
        String userOcid = "ocid1.user.oc1..aaaaaaaaneia4mf6ftdjims3upmi3ycm5yamf6xr4u5j6b4iacvl4idiw2va";
        String userPrivateKeyPath = "/Users/xin2012/.oci/oci_api_key.pem";
        String compartmentid = "ocid1.tenancy.oc1..aaaaaaaadkoovy5roxsjqrvbw7eykpgsfm2tb6yxvbcvezsvugac6nnsifiq";
        AuthenticationDetailsProvider provider = SimpleAuthenticationDetailsProvider.builder()
                .tenantId(tenancyOcid)
                .userId(userOcid)
                .fingerprint(userFingerprint)
                .privateKeySupplier(new SimplePrivateKeySupplier(userPrivateKeyPath))
                .build();
        Blockstorage blockstorage = new BlockstorageClient(provider);

        BlockstorageExample blockstorageExample = new BlockstorageExample();
        blockstorageExample.setBlockstorage(blockstorage);

        Identity identity = new IdentityClient(provider);
        String ad = blockstorageExample.getAvailableDomain(identity, compartmentid);
        blockstorageExample.createVolume("displayName", ad, compartmentid, 50L);
    }
}
