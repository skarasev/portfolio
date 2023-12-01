helm registry login harbor.company.com --username %Harbor.Username% --password-stdin <<< %Harbor.CLIKey% 

#Packing charts

charts=$(ls -d */ | cut -f1 -d'/') # Getting list of catalogs in the current directory, assuming charts only
echo $charts
for i in $charts
do
 echo "Packaging $i"
 helm package $i
done

#Pushing charts

chart_packages=$(ls *.tgz) # Getting list of packed charts
echo $chart_packages
for j in $chart_packages
do
 echo "Pushing $j"
 helm push $j oci://harbor.company.com/projectname/helm
done

helm registry logout harbor.company.com